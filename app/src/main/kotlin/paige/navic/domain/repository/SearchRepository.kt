/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.repository

import androidx.room3.RoomRawQuery
import kotlinx.coroutines.CancellationException
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.PlaylistDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.data.database.mapper.toEntity
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.SessionManager
import paige.navic.util.Logger

class SearchRepository(
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val songDao: SongDao,
	private val playlistDao: PlaylistDao,
	private val sessionManager: SessionManager,
	connectivityManager: ConnectivityManager
) {
	val isOnline = connectivityManager.isOnline

	suspend fun search(query: String): List<Any> {
		return if (isOnline.value) {
			try {
				val data = sessionManager.api.searchID3(query)

				albumDao.insertAlbumsIgnoringConflicts(data.albums.map { it.toEntity() })
				artistDao.insertArtistsIgnoringConflicts(data.artists.map { it.toEntity() })
				songDao.insertSongsIgnoringConflicts(data.songs.map { it.toEntity() })

				val albums = albumDao.getAlbumsByIds(data.albums.map { it.id })
				val artists = artistDao.getArtistsByIds(data.artists.map { it.id })
				val songs = songDao.getSongsByIds(data.songs.map { it.id })
				val localPlaylists = playlistDao.searchPlaylistsList(query)

				(albums.map { it.toDomainModel() }
					+ artists.map { it.toDomainModel() }
					+ songs.map { it.toDomainModel() }
					+ localPlaylists.map { it.toDomainModel() })
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Logger.e(
					"SearchRepository",
					"Online search failed despite network connection, falling back to local DB",
					e
				)
				performLocalSearch(query)
			}
		} else {
			Logger.i("SearchRepository", "Device offline, performing local search.")
			performLocalSearch(query)
		}
	}

	private suspend fun performLocalSearch(query: String): List<Any> {
		val tokens = query.trim().split(Regex("[\\s　]+")).filter { it.isNotEmpty() }
		if (tokens.isEmpty()) return emptyList()

		val localAlbums = albumDao.getAlbumsByQuery(
			buildFuzzyQuery("AlbumEntity", listOf("name", "artistName"), tokens)
		).rankByMatch(tokens, query) { listOf(it.album.name, it.album.artistName) }
			.take(MAX_LOCAL_RESULTS_PER_CATEGORY)
			.map { it.toDomainModel() }

		val localArtists = artistDao.searchArtistsFuzzy(
			buildFuzzyQuery("ArtistEntity", listOf("name"), tokens)
		).rankByMatch(tokens, query) { listOf(it.name) }
			.take(MAX_LOCAL_RESULTS_PER_CATEGORY)
			.map { it.toDomainModel() }

		val localSongs = songDao.searchSongsFuzzy(
			buildFuzzyQuery("SongEntity", listOf("title", "artistName", "albumTitle"), tokens)
		).rankByMatch(tokens, query) { listOf(it.title, it.artistName, it.albumTitle) }
			.take(MAX_LOCAL_RESULTS_PER_CATEGORY)
			.map { it.toDomainModel() }

		val localPlaylists = playlistDao.searchPlaylistsList(query).map { it.toDomainModel() }

		return localAlbums + localArtists + localSongs + localPlaylists
	}

	private fun buildFuzzyQuery(
		table: String,
		fields: List<String>,
		tokens: List<String>
	): RoomRawQuery {
		val tokenClauses = tokens.joinToString(" AND ") {
			fields.joinToString(" OR ") { field ->
				"$field LIKE '%' || ? || '%' ESCAPE '\\' COLLATE NOCASE"
			}.let { "($it)" }
		}
		val sql = "SELECT * FROM $table WHERE $tokenClauses LIMIT $LOCAL_SEARCH_PREFILTER_LIMIT"
		return RoomRawQuery(sql) { statement ->
			var index = 1
			tokens.forEach { token ->
				repeat(fields.size) {
					statement.bindText(index, token.escapeLikeWildcards())
					index++
				}
			}
		}
	}

	private fun String.escapeLikeWildcards(): String =
		replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

	private fun matchTier(field: String?, token: String): Int = when {
		field == null -> 0
		field.equals(token, ignoreCase = true) -> 3
		field.startsWith(token, ignoreCase = true) -> 2
		field.contains(token, ignoreCase = true) -> 1
		else -> 0
	}

	private fun <T> List<T>.rankByMatch(
		tokens: List<String>,
		query: String,
		fields: (T) -> List<String?>
	): List<T> {
		return sortedByDescending { item ->
			val values = fields(item)
			val tokenScore = tokens.sumOf { token -> values.maxOf { matchTier(it, token) } }
			val queryBonus = when {
				values.any { it.equals(query, ignoreCase = true) } -> EXACT_QUERY_BONUS
				values.any { it?.startsWith(query, ignoreCase = true) == true } -> PREFIX_QUERY_BONUS
				else -> 0
			}
			tokenScore + queryBonus
		}
	}

	companion object {
		private const val MAX_LOCAL_RESULTS_PER_CATEGORY = 20
		private const val LOCAL_SEARCH_PREFILTER_LIMIT = 500
		private const val EXACT_QUERY_BONUS = 100
		private const val PREFIX_QUERY_BONUS = 50
	}
}

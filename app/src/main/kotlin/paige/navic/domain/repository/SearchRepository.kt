/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.repository

import androidx.room3.RoomRawQuery
import kotlinx.coroutines.CancellationException
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistAliasDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.ArtistGroupDao
import paige.navic.data.database.dao.PlaylistDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.data.database.mapper.toEntity
import paige.navic.domain.manager.ArtistGroupManager
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.model.DomainArtist
import paige.navic.util.Logger

class SearchRepository(
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val artistAliasDao: ArtistAliasDao,
	private val artistGroupDao: ArtistGroupDao,
	private val songDao: SongDao,
	private val playlistDao: PlaylistDao,
	private val sessionManager: SessionManager,
	private val artistGroupManager: ArtistGroupManager,
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
				val remoteArtists = artistGroupManager.collapseArtists(
					artistDao.getArtistsByIds(data.artists.map { it.id }).map { it.toDomainModel() }
				)
				val artists = (remoteArtists + searchLocalArtists(query)).distinctBy { it.id }
				val songs = songDao.getSongsByIds(data.songs.map { it.id })
				val localPlaylists = playlistDao.searchPlaylistsList(query)

				(albums.map { it.toDomainModel() }
					+ artists
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

		val localArtists = searchLocalArtists(tokens, query)

		val localSongs = songDao.searchSongsFuzzy(
			buildFuzzyQuery("SongEntity", listOf("title", "artistName", "albumTitle"), tokens)
		).rankByMatch(tokens, query) { listOf(it.title, it.artistName, it.albumTitle) }
			.take(MAX_LOCAL_RESULTS_PER_CATEGORY)
			.map { it.toDomainModel() }

		val localPlaylists = playlistDao.searchPlaylistsList(query).map { it.toDomainModel() }

		return localAlbums + localArtists + localSongs + localPlaylists
	}

	private suspend fun searchLocalArtists(query: String): List<DomainArtist> {
		val tokens = query.trim().split(Regex("[\\s　]+")).filter { it.isNotEmpty() }
		return searchLocalArtists(tokens, query)
	}

	private suspend fun searchLocalArtists(tokens: List<String>, query: String): List<DomainArtist> {
		if (tokens.isEmpty()) return emptyList()
		val matchingMembers = artistGroupDao.searchMembersFuzzy(
			buildFuzzyQuery("ArtistGroupMemberEntity", listOf("name"), tokens)
		)
		val memberNamesByArtistId = matchingMembers.groupBy { it.artistId }
			.mapValues { (_, members) -> members.map { it.name } }

		val matchingAliases = artistAliasDao.searchAliasesFuzzy(
			buildFuzzyQuery("ArtistAliasEntity", listOf("name"), tokens)
		)
		val aliasNamesByGroup = matchingAliases.groupBy { it.groupKey }
			.mapValues { (_, aliases) -> aliases.map { it.name } }
		val preferredNamesByGroup = artistGroupDao.getAllGroups().mapNotNull { group ->
			val preferredName = group.preferredName ?: return@mapNotNull null
			if (tokens.all { preferredName.contains(it, ignoreCase = true) }) {
				group.groupKey to preferredName
			} else {
				null
			}
		}.toMap()
		val aliasMembers = artistGroupDao.getAllMembers()
			.filter { it.groupKey in (aliasNamesByGroup.keys + preferredNamesByGroup.keys) }
		val aliasNamesByArtistId = aliasMembers.groupBy { it.artistId }
			.mapValues { (_, members) ->
				members.flatMap { member ->
					aliasNamesByGroup[member.groupKey].orEmpty() +
						listOfNotNull(preferredNamesByGroup[member.groupKey])
				}.distinct()
			}

		val searchedArtists = artistDao.searchArtistsFuzzy(
			buildFuzzyQuery("ArtistEntity", listOf("name"), tokens)
		)
		val matchingArtistIds = (matchingMembers.map { it.artistId } + aliasMembers.map { it.artistId })
			.distinct()
		val artistEntities = (searchedArtists + artistDao.getArtistsByIds(matchingArtistIds))
			.distinctBy { it.artistId }
		return artistEntities.rankByMatch(tokens, query) { artist ->
			listOf(artist.name) + memberNamesByArtistId[artist.artistId].orEmpty() +
				aliasNamesByArtistId[artist.artistId].orEmpty()
		}.let { artists ->
			artistGroupManager.collapseArtists(artists.map { it.toDomainModel() })
		}.take(MAX_LOCAL_RESULTS_PER_CATEGORY)
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

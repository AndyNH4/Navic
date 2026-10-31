/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.quickplay

import android.content.Context
import kotlinx.coroutines.flow.first
import paige.navic.R
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.dao.PlaylistDao
import paige.navic.data.database.dao.RadioDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.entity.DownloadStatus
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.model.DomainRadio
import paige.navic.domain.model.DomainSong
import paige.navic.domain.repository.PlayerStateRepository

enum class QuickPlaySourceType {
	CONTINUE,
	RANDOM,
	FAVORITES,
	DOWNLOADS,
	PLAYLIST,
	RADIO;

	companion object {
		fun from(value: String?): QuickPlaySourceType =
			entries.firstOrNull { it.name == value } ?: CONTINUE
	}
}

data class QuickPlaySourceInfo(
	val type: QuickPlaySourceType,
	val id: String? = null,
	val title: String,
	val subtitle: String,
	val artworkUri: String? = null,
	val available: Boolean
)

sealed interface QuickPlayPlayback {
	data object Continue : QuickPlayPlayback
	data class Songs(val songs: List<DomainSong>) : QuickPlayPlayback
	data class RadioStation(val radio: DomainRadio) : QuickPlayPlayback
}

class QuickPlayRepository(
	private val context: Context,
	private val playerStateRepository: PlayerStateRepository,
	private val songDao: SongDao,
	private val downloadDao: DownloadDao,
	private val playlistDao: PlaylistDao,
	private val radioDao: RadioDao,
	private val sessionManager: SessionManager
) {
	suspend fun getSourceInfo(type: QuickPlaySourceType, id: String? = null): QuickPlaySourceInfo {
		return when (type) {
			QuickPlaySourceType.CONTINUE -> {
				val state = playerStateRepository.state.first()
				val currentSong = state.currentSong ?: state.queue.getOrNull(state.currentIndex)
				QuickPlaySourceInfo(
					type = type,
					title = context.getString(R.string.widget_continue),
					subtitle = currentSong?.title ?: context.getString(R.string.widget_continue_subtitle),
					artworkUri = currentSong?.coverArtId?.let(::coverUri),
					available = state.queue.isNotEmpty()
				)
			}

			QuickPlaySourceType.RANDOM -> songSourceInfo(
				type = type,
				title = context.getString(R.string.widget_random),
				songs = songDao.getRandomSongs(QUICK_PLAY_LIMIT).map { it.toDomainModel() }
			)

			QuickPlaySourceType.FAVORITES -> songSourceInfo(
				type = type,
				title = context.getString(R.string.widget_favorites),
				songs = favoriteSongs()
			)

			QuickPlaySourceType.DOWNLOADS -> songSourceInfo(
				type = type,
				title = context.getString(R.string.widget_downloads),
				songs = downloadedSongs()
			)

			QuickPlaySourceType.PLAYLIST -> {
				val playlist = playlistDao.getPlaylistById(requireSourceId(type, id))?.toDomainModel()
					?: throw IllegalStateException("Playlist is not cached")
				QuickPlaySourceInfo(
					type = type,
					id = id,
					title = playlist.name ?: context.getString(R.string.info_unknown),
					subtitle = trackCount(playlist.songs.size),
					artworkUri = playlist.coverArtId?.let(::coverUri),
					available = playlist.songs.isNotEmpty()
				)
			}

			QuickPlaySourceType.RADIO -> {
				val radio = findRadio(id) ?: throw IllegalStateException("Radio is not cached")
				QuickPlaySourceInfo(
					type = type,
					id = radio.id,
					title = radio.name,
					subtitle = context.getString(R.string.widget_radio),
					available = radio.streamUrl.isNotBlank()
				)
			}
		}
	}

	suspend fun getAvailableSources(): List<QuickPlaySourceInfo> {
		val builtIns = listOf(
			getSourceInfo(QuickPlaySourceType.CONTINUE),
			getSourceInfo(QuickPlaySourceType.RANDOM),
			getSourceInfo(QuickPlaySourceType.FAVORITES),
			getSourceInfo(QuickPlaySourceType.DOWNLOADS)
		)
		val playlists = playlistDao.getAllPlaylistsByName().map { playlist ->
			QuickPlaySourceInfo(
				type = QuickPlaySourceType.PLAYLIST,
				id = playlist.playlist.playlistId,
				title = playlist.playlist.name ?: context.getString(R.string.info_unknown),
				subtitle = trackCount(playlist.songs.size),
				artworkUri = playlist.playlist.coverArtId?.let(::coverUri),
				available = playlist.songs.isNotEmpty()
			)
		}
		val radios = radioDao.getRadios().map { radio ->
			QuickPlaySourceInfo(
				type = QuickPlaySourceType.RADIO,
				id = radio.radioId,
				title = radio.name,
				subtitle = context.getString(R.string.widget_radio),
				available = radio.streamUrl.isNotBlank()
			)
		}
		return builtIns + playlists + radios
	}

	suspend fun getPlayback(type: QuickPlaySourceType, id: String? = null): QuickPlayPlayback {
		if (type == QuickPlaySourceType.CONTINUE) return QuickPlayPlayback.Continue
		if (type == QuickPlaySourceType.RADIO) {
			return QuickPlayPlayback.RadioStation(
				findRadio(id) ?: throw IllegalStateException("Radio is not cached")
			)
		}
		val songs = when (type) {
			QuickPlaySourceType.RANDOM -> songDao.getRandomSongs(QUICK_PLAY_LIMIT).map { it.toDomainModel() }
			QuickPlaySourceType.FAVORITES -> favoriteSongs()
			QuickPlaySourceType.DOWNLOADS -> downloadedSongs()
			QuickPlaySourceType.PLAYLIST -> playlistDao.getPlaylistById(requireSourceId(type, id))
				?.toDomainModel()?.songs?.take(QUICK_PLAY_LIMIT).orEmpty()
		}
		if (songs.isEmpty()) throw IllegalStateException("No cached songs for quick play")
		return QuickPlayPlayback.Songs(songs)
	}

	private fun songSourceInfo(
		type: QuickPlaySourceType,
		title: String,
		songs: List<DomainSong>
	) = QuickPlaySourceInfo(
		type = type,
		title = title,
		subtitle = trackCount(songs.size),
		artworkUri = songs.firstOrNull()?.coverArtId?.let(::coverUri),
		available = songs.isNotEmpty()
	)

	private suspend fun favoriteSongs(): List<DomainSong> = songDao.getStarredSongsList()
		.map { it.toDomainModel() }
		.sortedByDescending { it.starredAt }
		.take(QUICK_PLAY_LIMIT)

	private suspend fun downloadedSongs(): List<DomainSong> {
		val ids = downloadDao.getAllDownloadsList()
			.filter { it.status == DownloadStatus.DOWNLOADED }
			.map { it.songId }
		if (ids.isEmpty()) return emptyList()
		return songDao.getSongsByIds(ids)
			.map { it.toDomainModel() }
			.sortedBy { it.title.lowercase() }
			.take(QUICK_PLAY_LIMIT)
	}

	private suspend fun findRadio(id: String?) = radioDao.getRadios()
		.map { it.toDomainModel() }
		.firstOrNull { it.id == id }

	private fun requireSourceId(type: QuickPlaySourceType, id: String?): String =
		id ?: throw IllegalArgumentException("${type.name} requires a source id")

	private fun coverUri(id: String): String = sessionManager.getCoverArtUrl(id)

	private fun trackCount(count: Int): String = context.getString(R.string.widget_track_count, count)

	companion object {
		const val QUICK_PLAY_LIMIT = 100
	}
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.entity.DownloadEntity
import paige.navic.data.database.entity.DownloadStatus
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.StorageManager
import paige.navic.domain.model.DomainSong
import paige.navic.domain.repository.SongRepository

@Immutable
data class DownloadedSongItem(
	val song: DomainSong,
	val download: DownloadEntity,
	val fileSize: Long
)

class SettingsDownloadedSongsViewModel(
	private val downloadManager: DownloadManager,
	private val songRepository: SongRepository,
	private val songDao: SongDao,
	private val storageManager: StorageManager,
	connectivityManager: ConnectivityManager
) : ViewModel() {

	val items: StateFlow<ImmutableList<DownloadedSongItem>?>
		field = MutableStateFlow(null)

	val selectedSong: StateFlow<DomainSong?>
		field = MutableStateFlow(null)

	val selectedSongStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	val isOnline = connectivityManager.isOnline

	private val songMetadata = MutableStateFlow<Map<String, DomainSong>?>(null)

	init {
		viewModelScope.launch(Dispatchers.IO) {
			downloadManager.allDownloads
				.map { downloads -> downloads.map { it.songId }.toSet() }
				.distinctUntilChanged()
				.collect { ids -> reloadSongMetadata(ids) }
		}
		viewModelScope.launch(Dispatchers.IO) {
			combine(downloadManager.allDownloads, songMetadata) { downloads, metadata ->
				metadata?.let { buildItems(downloads, it) }
			}.collect { built ->
				if (built != null) items.value = built
			}
		}
	}

	private suspend fun reloadSongMetadata(ids: Set<String>) {
		songMetadata.value = if (ids.isEmpty()) {
			emptyMap()
		} else {
			songDao.getSongsByIds(ids.toList())
				.associate { it.songId to it.toDomainModel() }
		}
	}

	private suspend fun refreshSongMetadata(songId: String) {
		val entity = songDao.getSongById(songId) ?: return
		val current = songMetadata.value ?: return
		songMetadata.value = current + (entity.songId to entity.toDomainModel())
	}

	private fun buildItems(
		downloads: List<DownloadEntity>,
		metadata: Map<String, DomainSong>
	): ImmutableList<DownloadedSongItem> {
		return downloads.mapNotNull { download ->
			val song = metadata[download.songId] ?: return@mapNotNull null
			val fileSize = if (download.status == DownloadStatus.DOWNLOADED) {
				download.filePath?.let(storageManager::getFileSize) ?: 0L
			} else {
				0L
			}
			DownloadedSongItem(song = song, download = download, fileSize = fileSize)
		}.sortedWith(
			compareBy<DownloadedSongItem> {
				when (it.download.status) {
					DownloadStatus.DOWNLOADING -> 0
					DownloadStatus.FAILED -> 1
					else -> 2
				}
			}
				.thenBy { it.song.artistName?.lowercase() }
				.thenBy { it.song.albumTitle?.lowercase() }
				.thenBy { it.song.discNumber ?: 0 }
				.thenBy { it.song.trackNumber ?: 0 }
				.thenBy { it.song.title.lowercase() }
		).toImmutableList()
	}

	fun selectSong(song: DomainSong) {
		viewModelScope.launch(Dispatchers.IO) {
			selectedSong.value = song
			selectedSongStarred.value = songRepository.isSongStarred(song)
			selectedSongRating.value = songRepository.getSongRating(song)
		}
	}

	fun clearSelection() {
		selectedSong.value = null
	}

	fun starSong(isStarred: Boolean) {
		viewModelScope.launch(Dispatchers.IO) {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				if (isStarred) {
					songRepository.starSong(selection)
				} else {
					songRepository.unstarSong(selection)
				}
				selectedSongStarred.value = isStarred
				refreshSongMetadata(selection.id)
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch(Dispatchers.IO) {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				songRepository.rateSong(selection, rating)
				selectedSongRating.value = rating
				refreshSongMetadata(selection.id)
			}
		}
	}

	fun downloadSong(song: DomainSong) {
		downloadManager.downloadSong(song)
	}

	fun cancelDownload(songId: String) {
		downloadManager.cancelDownload(songId)
	}

	fun deleteDownload(songId: String) {
		downloadManager.deleteDownload(songId)
	}
}

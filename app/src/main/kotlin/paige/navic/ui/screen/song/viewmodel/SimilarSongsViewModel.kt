/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.song.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.model.DomainSong
import paige.navic.domain.repository.AudioMuseRepository
import paige.navic.domain.repository.SongRepository
import paige.navic.ui.core.UiState

class SimilarSongsViewModel(
	private val songId: String,
	private val audioMuseRepository: AudioMuseRepository,
	private val songRepository: SongRepository,
	private val downloadManager: DownloadManager
) : ViewModel() {
	val songsState: StateFlow<UiState<ImmutableList<DomainSong>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainSong>>>(UiState.Loading())

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = persistentListOf()
		)

	val selectedSong: StateFlow<DomainSong?>
		field = MutableStateFlow(null)

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	init {
		refreshSimilarSongs()
	}

	fun refreshSimilarSongs() {
		viewModelScope.launch {
			songsState.value = UiState.Loading(data = songsState.value.data)
			try {
				val songs = audioMuseRepository.getSimilarSongs(songId).toImmutableList()
				songsState.value = UiState.Success(data = songs)
			} catch (error: Exception) {
				songsState.value = UiState.Error(error = error, data = songsState.value.data)
			}
		}
	}

	fun selectSong(song: DomainSong) {
		viewModelScope.launch {
			selectedSong.value = song
			starred.value = songRepository.isSongStarred(song)
			selectedSongRating.value = songRepository.getSongRating(song)
		}
	}

	fun clearSelection() {
		selectedSong.value = null
	}

	fun starSong(isStarred: Boolean) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				if (isStarred) {
					songRepository.starSong(selection)
				} else {
					songRepository.unstarSong(selection)
				}
				starred.value = isStarred
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				songRepository.rateSong(selection, rating)
				selectedSongRating.value = rating
			}
		}
	}

	fun clearError() {
		songsState.value = UiState.Success(songsState.value.data ?: persistentListOf())
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

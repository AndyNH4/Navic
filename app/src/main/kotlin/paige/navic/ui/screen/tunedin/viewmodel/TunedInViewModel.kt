/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.tunedin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import paige.navic.R
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.model.DomainMix
import paige.navic.domain.repository.TunedInRepository
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.util.Logger

class TunedInViewModel(
	private val repository: TunedInRepository,
	private val connectivityManager: ConnectivityManager,
	private val snackBarManager: SnackBarManager
) : ViewModel() {

	val mixes: StateFlow<List<DomainMix>>
		field = MutableStateFlow(emptyList())

	/** Id of the mix currently being generated, null when idle */
	val loadingMixId: StateFlow<String?>
		field = MutableStateFlow(null)

	init {
		viewModelScope.launch {
			connectivityManager.isOnline.collect { isOnline ->
				try {
					mixes.value = repository.generateMixes(isOnline)
				} catch (e: Exception) {
					Logger.e("TunedInViewModel", "Failed to generate mixes", e)
				}
			}
		}
	}

	fun refreshMixes() {
		viewModelScope.launch {
			try {
				mixes.value = repository.generateMixes(connectivityManager.isOnline.value)
			} catch (e: Exception) {
				Logger.e("TunedInViewModel", "Failed to refresh mixes", e)
			}
		}
	}

	fun playMix(player: MediaPlayerViewModel, mix: DomainMix) {
		if (loadingMixId.value != null) return
		loadingMixId.value = mix.id
		viewModelScope.launch {
			try {
				val songs = repository.fetchMixSongs(mix)
				if (songs.isEmpty()) {
					snackBarManager.notify(R.string.tunedin_notice_empty_mix)
				} else {
					player.playNow(songs.shuffled(), startIndex = 0)
				}
			} finally {
				loadingMixId.value = null
			}
		}
	}
}

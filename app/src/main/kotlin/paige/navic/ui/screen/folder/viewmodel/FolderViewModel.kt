/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.folder.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.model.DomainFolder
import paige.navic.domain.model.DomainFolderCollection
import paige.navic.domain.model.DomainFolderContent
import paige.navic.domain.repository.FolderRepository
import paige.navic.ui.core.UiState
import kotlin.time.Duration

class FolderViewModel(
	private val repository: FolderRepository,
	private val sessionManager: SessionManager,
	private val connectivityManager: ConnectivityManager
) : ViewModel() {
	val contentState: StateFlow<UiState<DomainFolderContent>>
		field = MutableStateFlow<UiState<DomainFolderContent>>(UiState.Loading())

	val isRefreshing: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val path: StateFlow<List<DomainFolder>>
		field = MutableStateFlow(emptyList())

	val isOnline: StateFlow<Boolean>
		field = MutableStateFlow(true)

	val gridState = LazyGridState()

	private val cache = mutableMapOf<String, DomainFolderContent>()
	private var loadJob: Job? = null

	init {
		viewModelScope.launch {
			connectivityManager.isOnline.collect { online ->
				val cameBackOnline = online && !isOnline.value
				isOnline.value = online
				if (cameBackOnline && contentState.value.data == null) {
					load(path.value)
				}
			}
		}
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) load(path.value) }
		}
	}

	fun navigateInto(folder: DomainFolder) {
		val newPath = path.value + folder
		path.value = newPath
		load(newPath)
	}

	fun navigateUp() {
		if (path.value.isEmpty()) return
		val newPath = path.value.dropLast(1)
		path.value = newPath
		load(newPath)
	}

	fun refresh() {
		load(path.value, isRefresh = true)
	}

	fun clearError() {
		contentState.value = UiState.Success(
			contentState.value.data
				?: DomainFolderContent(persistentListOf(), persistentListOf())
		)
	}

	fun currentCollection(): DomainFolderCollection? {
		val content = contentState.value.data ?: return null
		if (content.songs.isEmpty()) return null
		val current = path.value.lastOrNull()
		return DomainFolderCollection(
			id = current?.id ?: "folders-root",
			name = current?.name,
			coverArtId = content.songs.firstNotNullOfOrNull { it.coverArtId },
			duration = content.songs.fold(Duration.ZERO) { total, song -> total + song.duration },
			songCount = content.songs.size,
			songs = content.songs
		)
	}

	private fun load(currentPath: List<DomainFolder>, isRefresh: Boolean = false) {
		val key = currentPath.lastOrNull()?.id ?: ROOT_KEY
		loadJob?.cancel()
		loadJob = viewModelScope.launch {
			if (!connectivityManager.isOnline.value) return@launch
			val cached = cache[key]
			if (!isRefresh && cached != null) {
				contentState.value = UiState.Success(cached)
				return@launch
			}
			if (cached == null) {
				contentState.value = UiState.Loading(data = contentState.value.data)
			}
			isRefreshing.value = true
			try {
				val content = currentPath.lastOrNull()?.let { repository.getContent(it) }
					?: DomainFolderContent(
						folders = repository.getMusicFolders(),
						songs = persistentListOf()
					)
				cache[key] = content
				contentState.value = UiState.Success(content)
			} catch (error: Exception) {
				contentState.value = UiState.Error(error = error, data = cached)
			} finally {
				isRefreshing.value = false
			}
		}
	}

	private companion object {
		const val ROOT_KEY = "root"
	}
}

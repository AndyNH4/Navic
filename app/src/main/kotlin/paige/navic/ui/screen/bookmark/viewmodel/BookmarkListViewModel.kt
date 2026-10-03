/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.bookmark.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.model.DomainBookmark
import paige.navic.domain.repository.BookmarkRepository
import paige.navic.ui.core.UiState

class BookmarkListViewModel(
	private val repository: BookmarkRepository,
	private val sessionManager: SessionManager
) : ViewModel() {
	val bookmarksState: StateFlow<UiState<List<DomainBookmark>>>
		field = MutableStateFlow<UiState<List<DomainBookmark>>>(UiState.Loading())

	val isRefreshing: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect {
				refreshBookmarks()
			}
		}
	}

	fun refreshBookmarks() {
		viewModelScope.launch {
			val currentState = bookmarksState.value
			val hasData = currentState is UiState.Success && currentState.data.isNotEmpty()

			if (hasData) {
				isRefreshing.value = true
			} else {
				bookmarksState.value = UiState.Loading()
			}

			try {
				val bookmarks = repository.getBookmarks()
				bookmarksState.value = UiState.Success(bookmarks)
			} catch (e: Exception) {
				bookmarksState.value = UiState.Error(e)
			} finally {
				isRefreshing.value = false
			}
		}
	}
}

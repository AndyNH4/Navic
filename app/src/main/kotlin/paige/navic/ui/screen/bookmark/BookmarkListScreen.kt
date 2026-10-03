/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.bookmark

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.R
import paige.navic.di.LocalBottomBarScrollManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.settings.BottomBarVisibilityMode
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.component.common.ContentUnavailable
import paige.navic.ui.component.dialog.DeletionDialog
import paige.navic.ui.component.dialog.DeletionEndpoint
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.component.layout.PullToRefreshBox
import paige.navic.ui.component.layout.RootBottomBar
import paige.navic.ui.component.layout.artGridError
import paige.navic.ui.core.UiState
import paige.navic.ui.screen.bookmark.component.BookmarkListItem
import paige.navic.ui.screen.bookmark.viewmodel.BookmarkListViewModel
import paige.navic.ui.util.withoutTop

@Composable
fun BookmarkListScreen() {
	val viewModel = koinViewModel<BookmarkListViewModel>()

	val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
	val isRefreshingFlow by viewModel.isRefreshing.collectAsStateWithLifecycle()

	val player = koinInject<MediaPlayerViewModel>()
	val preferenceManager = koinInject<PreferenceManager>()

	var deletionId by remember { mutableStateOf<String?>(null) }

	Scaffold(
		topBar = { NestedTopBar({ Text(stringResource(R.string.title_bookmarks)) }) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { contentPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = contentPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = bookmarksState !is UiState.Loading && !isRefreshingFlow,
			onRefresh = { viewModel.refreshBookmarks() },
			key = listOf(bookmarksState, isRefreshingFlow)
		) {
			Crossfade(bookmarksState) { stateValue ->
				LazyVerticalGrid(
					modifier = Modifier.fillMaxSize(),
					columns = GridCells.Fixed(1),
					contentPadding = contentPadding.withoutTop(),
					state = viewModel.gridState,
					verticalArrangement = if ((stateValue as? UiState.Success)?.data?.isEmpty() == true)
						Arrangement.Center
					else Arrangement.Top
				) {
					when (stateValue) {
						is UiState.Loading -> {
							return@LazyVerticalGrid
						}

						is UiState.Error -> artGridError(stateValue)
						is UiState.Success -> {
							items(stateValue.data, { it.songId }) { bookmark ->
								BookmarkListItem(
									modifier = Modifier.animateItem(fadeInSpec = null),
									bookmark = bookmark,
									onClick = {
										player.playSongAtPosition(
											song = bookmark.song,
											positionMs = bookmark.positionMs
										)
									},
									onSwipeToDelete = { deletionId = bookmark.songId }
								)
							}
							if (stateValue.data.isEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									ContentUnavailable(
										label = stringResource(R.string.info_no_bookmarks)
									)
								}
							}
						}
					}
				}
			}
		}
	}

	DeletionDialog(
		endpoint = DeletionEndpoint.BOOKMARK,
		id = deletionId,
		onIdClear = { deletionId = null },
		onRefresh = viewModel::refreshBookmarks
	)
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.folder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.R
import paige.navic.di.LocalBottomBarScrollManager
import paige.navic.di.LocalNavStack
import paige.navic.di.LocalSizeClass
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.settings.BottomBarVisibilityMode
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.ArrowBack
import paige.navic.icons.outlined.Queue
import paige.navic.icons.outlined.Shuffle
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.component.common.ContentUnavailable
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.component.layout.PullToRefreshBox
import paige.navic.ui.component.layout.RootBottomBar
import paige.navic.ui.component.layout.RootTopBar
import paige.navic.ui.component.layout.TopBarButton
import paige.navic.ui.component.layout.artGridError
import paige.navic.ui.component.snackbar.ErrorSnackBar
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.PersistentViewModelStoreOwner
import paige.navic.ui.screen.folder.component.FolderRow
import paige.navic.ui.screen.folder.component.FolderSongRow
import paige.navic.ui.screen.folder.viewmodel.FolderViewModel
import paige.navic.ui.util.withoutTop
import paige.navic.ui.viewmodel.RootViewModel

@Composable
fun FolderListScreen(
	nested: Boolean
) {
	val viewModel = koinViewModel<FolderViewModel>(
		viewModelStoreOwner = if (nested) {
			LocalViewModelStoreOwner.current!!
		} else {
			koinInject<PersistentViewModelStoreOwner>()
		}
	)

	val contentState by viewModel.contentState.collectAsStateWithLifecycle()
	val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
	val path by viewModel.path.collectAsStateWithLifecycle()
	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

	val player = koinInject<MediaPlayerViewModel>()
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	val rootViewModel = koinViewModel<RootViewModel>()
	LaunchedEffect(Unit) {
		rootViewModel.events.collect { event ->
			if (event is RootViewModel.Event.ScrollToTop) {
				viewModel.gridState.animateScrollToItem(0)
			}
		}
	}

	BackHandler(enabled = path.isNotEmpty()) {
		viewModel.navigateUp()
	}

	Scaffold(
		topBar = {
			if (!nested && path.isEmpty()) {
				RootTopBar({ Text(stringResource(R.string.title_folders)) }, scrollBehavior)
			} else {
				NestedTopBar(
					title = { Text(path.lastOrNull()?.name ?: stringResource(R.string.title_folders)) },
					navigationAction = {
						TopBarButton(
							modifier = Modifier.padding(start = 20.dp, end = 13.dp),
							onClick = dropUnlessResumed {
								if (path.isNotEmpty()) {
									viewModel.navigateUp()
								} else if (backStack.size > 1) {
									backStack.removeLastOrNull()
								}
							}
						) {
							Icon(
								imageVector = Icons.Outlined.ArrowBack,
								contentDescription = stringResource(R.string.action_navigate_back)
							)
						}
					},
					actions = {
						val songs = contentState.data?.songs.orEmpty()
						if (songs.isNotEmpty()) {
							TopBarButton(
								onClick = dropUnlessResumed {
									viewModel.currentCollection()?.let { player.playNow(it) }
								}
							) {
								Icon(
									imageVector = Icons.Filled.Play,
									contentDescription = stringResource(R.string.action_play)
								)
							}
							TopBarButton(
								onClick = dropUnlessResumed {
									viewModel.currentCollection()?.let { player.shufflePlay(it) }
								}
							) {
								Icon(
									imageVector = Icons.Outlined.Shuffle,
									contentDescription = stringResource(R.string.action_shuffle)
								)
							}
							TopBarButton(
								onClick = dropUnlessResumed {
									viewModel.currentCollection()?.let { player.addToQueue(it) }
								}
							) {
								Icon(
									imageVector = Icons.Outlined.Queue,
									contentDescription = stringResource(R.string.action_add_to_queue)
								)
							}
						}
					}
				)
			}
		},
		bottomBar = {
			val sizeClass = LocalSizeClass.current
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible =
				preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (!nested
				|| (sizeClass.widthSizeClass < WindowWidthSizeClass.Medium && preferVisible)) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = contentState !is UiState.Loading && !isRefreshing,
			onRefresh = { viewModel.refresh() },
			key = listOf(contentState, isRefreshing)
		) {
			Crossfade(contentState) { stateValue ->
				LazyVerticalGrid(
					modifier = Modifier
						.fillMaxSize()
						.then(
							if (!nested && path.isEmpty())
								Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
							else Modifier
						),
					columns = GridCells.Fixed(1),
					contentPadding = innerPadding.withoutTop(),
					state = viewModel.gridState,
					verticalArrangement = if (stateValue.data?.let {
							it.folders.isEmpty() && it.songs.isEmpty()
						} == true)
						Arrangement.Center
					else Arrangement.Top
				) {
					when (stateValue) {
						is UiState.Loading -> {
							if (!isOnline && stateValue.data == null) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									ContentUnavailable(
										label = stringResource(R.string.info_folders_offline)
									)
								}
							}
						}

						is UiState.Error -> {
							if (!isOnline && stateValue.data == null) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									ContentUnavailable(
										label = stringResource(R.string.info_folders_offline)
									)
								}
							} else {
								artGridError(stateValue)
							}
						}

						is UiState.Success -> {
							items(
								stateValue.data.folders,
								key = { "folder-${it.id}" }
							) { folder ->
								FolderRow(
									folder = folder,
									onClick = dropUnlessResumed {
										viewModel.navigateInto(folder)
									}
								)
							}
							items(
								stateValue.data.songs,
								key = { "song-${it.id}" }
							) { song ->
								FolderSongRow(
									song = song,
									onClick = dropUnlessResumed {
										val index = stateValue.data.songs.indexOf(song)
										val collection = viewModel.currentCollection()
										if (collection != null && index >= 0) {
											player.playNow(collection, startIndex = index)
										} else {
											player.playNow(song)
										}
									}
								)
							}
							if (stateValue.data.folders.isEmpty() && stateValue.data.songs.isEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									ContentUnavailable(
										label = stringResource(
											if (!isOnline) R.string.info_folders_offline
											else R.string.info_folder_empty
										)
									)
								}
							}
						}
					}
				}
			}
		}
	}

	ErrorSnackBar(
		error = (contentState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)
}

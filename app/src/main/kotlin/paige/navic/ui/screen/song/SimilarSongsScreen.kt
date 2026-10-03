/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.R
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.DomainSong
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.component.dialog.QueueDuplicateDialog
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.component.layout.PullToRefreshBox
import paige.navic.ui.component.snackbar.ErrorSnackBar
import paige.navic.ui.core.UiState
import paige.navic.ui.screen.share.dialog.ShareDialog
import paige.navic.ui.screen.song.component.songListScreenContent
import paige.navic.ui.screen.song.viewmodel.SimilarSongsViewModel
import paige.navic.ui.util.withoutTop
import kotlin.time.Duration

@Composable
fun SimilarSongsScreen(
	songId: String
) {
	val viewModel = koinViewModel<SimilarSongsViewModel>(
		key = songId,
		parameters = { parametersOf(songId) }
	)
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val songsState by viewModel.songsState.collectAsStateWithLifecycle()
	val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
	val starred by viewModel.starred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()
	val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var songToQueue by remember { mutableStateOf<DomainSong?>(null) }

	Scaffold(
		topBar = {
			NestedTopBar({ Text(stringResource(R.string.title_similar_songs)) })
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = songsState !is UiState.Loading,
			onRefresh = { viewModel.refreshSimilarSongs() },
			key = songsState
		) {
			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				contentPadding = innerPadding.withoutTop(),
				verticalArrangement = if ((songsState as? UiState.Success)?.data?.isEmpty() == true)
					Arrangement.Center
				else Arrangement.spacedBy(12.dp),
				state = rememberLazyListState()
			) {
				songListScreenContent(
					state = songsState,
					selectedSongIsStarred = starred,
					selectedSongRating = selectedSongRating,
					selectedSong = selectedSong,
					onUpdateSelection = { viewModel.selectSong(it) },
					onClearSelection = { viewModel.clearSelection() },
					onSetShareId = { newShareId ->
						shareId = newShareId
					},
					onSetStarred = { viewModel.starSong(it) },
					onPlayNext = { song ->
						if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
							songToQueue = song
						} else {
							player.playNextSingle(song)
						}
					},
					onAddToQueue = { song ->
						if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
							songToQueue = song
						} else {
							player.addToQueueSingle(song)
						}
					},
					onPlaySong = { song ->
						songsState.data?.let { songs ->
							player.playNow(songs, songs.indexOf(song).coerceAtLeast(0))
						}
					},
					onSetRating = { viewModel.rateSelectedSong(it) },
					onDownload = { viewModel.downloadSong(it) },
					allDownloads = allDownloads,
					onCancelDownload = { viewModel.cancelDownload(it.id) },
					onDeleteDownload = { viewModel.deleteDownload(it.id) }
				)
			}
		}
	}

	ErrorSnackBar(
		error = (songsState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	if (songToQueue != null) {
		QueueDuplicateDialog(
			onDismissRequest = { songToQueue = null },
			onConfirm = {
				songToQueue?.let { player.addToQueueSingle(it) }
			}
		)
	}
}

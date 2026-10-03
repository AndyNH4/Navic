/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.R
import paige.navic.data.database.entity.DownloadStatus
import paige.navic.di.LocalSizeClass
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.component.common.ContentUnavailable
import paige.navic.ui.component.common.SongRow
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.component.layout.NestedTopBarDefaults
import paige.navic.ui.screen.settings.viewmodel.DownloadedSongItem
import paige.navic.ui.screen.settings.viewmodel.SettingsDownloadedSongsViewModel
import paige.navic.ui.screen.share.dialog.ShareDialog
import kotlin.time.Duration

@Composable
fun SettingsDownloadedSongsScreen() {
	val viewModel = koinViewModel<SettingsDownloadedSongsViewModel>()

	val sizeClass = LocalSizeClass.current
	val hideBack = sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

	val items by viewModel.items.collectAsStateWithLifecycle()
	val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSongStarred by viewModel.selectedSongStarred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()
	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

	val player = koinInject<MediaPlayerViewModel>()

	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(R.string.option_downloaded_songs)) },
				navigationAction = {
					if (!hideBack) {
						NestedTopBarDefaults.NavigationAction()
					}
				}
			)
		}
	) { innerPadding ->
		val currentItems = items
		when {
			currentItems == null -> {
				Box(
					modifier = Modifier.fillMaxSize().padding(innerPadding),
					contentAlignment = Alignment.Center
				) {
					CircularProgressIndicator()
				}
			}

			currentItems.isEmpty() -> {
				ContentUnavailable(
					label = stringResource(R.string.info_no_downloaded_songs),
					modifier = Modifier.padding(innerPadding)
				)
			}

			else -> {
				val downloadedCount = remember(currentItems) {
					currentItems.count { it.download.status == DownloadStatus.DOWNLOADED }
				}
				val downloadingCount = remember(currentItems) {
					currentItems.count { it.download.status == DownloadStatus.DOWNLOADING }
				}
				val totalSize = remember(currentItems) {
					currentItems
						.filter { it.download.status == DownloadStatus.DOWNLOADED }
						.sumOf { it.fileSize }
				}

				val countText = pluralStringResource(
					R.plurals.count_songs,
					downloadedCount,
					downloadedCount
				)
				val summaryText = if (downloadingCount > 0) {
					countText + " • " + formatFileSize(totalSize) +
						" • " + stringResource(R.string.info_downloading_count, downloadingCount)
				} else {
					countText + " • " + formatFileSize(totalSize)
				}

				LazyColumn(
					modifier = Modifier.fillMaxSize().padding(innerPadding),
					contentPadding = PaddingValues(bottom = 16.dp),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					item(key = "summary") {
						Text(
							text = summaryText,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier
								.widthIn(max = 400.dp)
								.fillMaxWidth()
								.padding(top = 8.dp)
								.padding(horizontal = 16.dp)
								.heightIn(min = 32.dp)
						)
					}
					itemsIndexed(
						items = currentItems,
						key = { _, item -> item.song.id }
					) { index, item ->
						SongRow(
							modifier = Modifier.fillMaxWidth(),
							song = item.song,
							selected = selectedSong == item.song,
							onClick = {
								player.playNow(currentItems.map { it.song }, index)
							},
							onLongClick = { viewModel.selectSong(item.song) },
							onDismissRequest = { viewModel.clearSelection() },
							starredState = if (selectedSong == item.song) {
								selectedSongStarred
							} else {
								item.song.starredAt != null
							},
							onAddStar = { viewModel.starSong(true) },
							onRemoveStar = { viewModel.starSong(false) },
							download = item.download,
							onDownload = { viewModel.downloadSong(item.song) },
							onCancelDownload = { viewModel.cancelDownload(item.song.id) },
							onDeleteDownload = { viewModel.deleteDownload(item.song.id) },
							onPlayNext = { player.playNextSingle(item.song) },
							onAddToQueue = { player.addToQueueSingle(item.song) },
							onShare = { shareId = item.song.id },
							isOnline = isOnline,
							rating = selectedSongRating,
							onSetRating = { viewModel.rateSelectedSong(it) },
							supportingExtra = {
								DownloadedSongStatusText(item)
							}
						)
					}
				}
			}
		}
	}

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)
}

@Composable
private fun DownloadedSongStatusText(item: DownloadedSongItem) {
	when (item.download.status) {
		DownloadStatus.DOWNLOADING -> Text(
			text = stringResource(R.string.info_status_downloading) +
				" ${(item.download.progress * 100).toInt()}%",
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.primary
		)

		DownloadStatus.FAILED -> Text(
			text = stringResource(R.string.info_download_failed),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.error
		)

		else -> Text(
			text = formatFileSize(item.fileSize),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

private fun formatFileSize(bytes: Long): String {
	val mb = bytes.toDouble() / (1024 * 1024)
	return if (mb >= 1024) {
		val gb = mb / 1024
		"${(gb * 100).toInt() / 100.0} GB"
	} else if (mb >= 1) {
		"${(mb * 10).toInt() / 10.0} MB"
	} else {
		"${bytes / 1024} KB"
	}
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.starred.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.data.database.entity.DownloadEntity
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.DomainExplicitStatus
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.settings.ExplicitContentPlayback
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Queue
import paige.navic.icons.outlined.QueuePlayNext
import paige.navic.ui.component.common.SongRow
import paige.navic.ui.component.common.SwipeToDismissBox

@Composable
fun StarredScreenSongRow(
	song: DomainSong,
	selected: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	isOnline: Boolean = false,
	onDismissRequest: () -> Unit,
	onRemoveStar: () -> Unit,
	onAddStar: () -> Unit,
	onShare: () -> Unit,
	starredState: Boolean,
	download: DownloadEntity? = null,
	onDownload: () -> Unit,
	onCancelDownload: () -> Unit,
	onDeleteDownload: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	rating: Int,
	onSetRating: (Int) -> Unit,
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val dismissState = rememberSwipeToDismissBoxState()
	val scope = rememberCoroutineScope()

	val isExplicit = song.explicitStatus == DomainExplicitStatus.Explicit
		&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed

	SwipeToDismissBox(
		state = dismissState,
		gesturesEnabled = !isExplicit,
		onDismiss = {
			if (it == SwipeToDismissBoxValue.StartToEnd) onAddToQueue()
			if (it == SwipeToDismissBoxValue.EndToStart) onPlayNext()
			scope.launch { dismissState.reset() }
		},
		backgroundContent = {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(MaterialTheme.shapes.largeIncreased)
					.background(MaterialTheme.colorScheme.primaryContainer)
					.padding(horizontal = 20.dp)
			) {
				when (dismissState.dismissDirection) {
					SwipeToDismissBoxValue.StartToEnd -> {
						Icon(
							imageVector = Icons.Outlined.Queue,
							contentDescription = stringResource(R.string.action_add_to_queue),
							tint = MaterialTheme.colorScheme.onPrimaryContainer,
							modifier = Modifier.align(Alignment.CenterStart)
						)
					}

					SwipeToDismissBoxValue.EndToStart -> {
						Icon(
							imageVector = Icons.Outlined.QueuePlayNext,
							contentDescription = stringResource(R.string.action_play_next),
							tint = MaterialTheme.colorScheme.onPrimaryContainer,
							modifier = Modifier.align(Alignment.CenterEnd)
						)
					}

					else -> {}
				}
			}
		}
	) {
		SongRow(
			song = song,
			selected = selected,
			onClick = onClick,
			onLongClick = onLongClick,
			isOnline = isOnline,
			onDismissRequest = onDismissRequest,
			onRemoveStar = onRemoveStar,
			onAddStar = onAddStar,
			onShare = onShare,
			starredState = starredState,
			download = download,
			onDownload = onDownload,
			onCancelDownload = onCancelDownload,
			onDeleteDownload = onDeleteDownload,
			onPlayNext = onPlayNext,
			onAddToQueue = onAddToQueue,
			rating = rating,
			onSetRating = onSetRating
		)
	}
}

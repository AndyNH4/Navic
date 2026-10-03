/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.bookmark.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
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
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.DomainBookmark
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Delete
import paige.navic.ui.component.common.CoverArt
import paige.navic.ui.component.common.SwipeToDismissBox
import paige.navic.util.toHoursMinutesSeconds
import paige.navic.util.toReadableDateTime
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BookmarkListItem(
	modifier: Modifier = Modifier,
	bookmark: DomainBookmark,
	onClick: () -> Unit,
	onSwipeToDelete: () -> Unit
) {
	val scope = rememberCoroutineScope()
	val dismissState = rememberSwipeToDismissBoxState()
	val preferenceManager = koinInject<PreferenceManager>()

	SwipeToDismissBox(
		state = dismissState,
		onDismiss = {
			if (it != SwipeToDismissBoxValue.Settled) onSwipeToDelete()
			scope.launch { dismissState.reset() }
		},
		backgroundContent = {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(MaterialTheme.shapes.small)
					.background(MaterialTheme.colorScheme.errorContainer)
					.padding(horizontal = 20.dp)
			) {
				Icon(
					imageVector = Icons.Outlined.Delete,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onErrorContainer,
					modifier = Modifier.align(
						when (dismissState.dismissDirection) {
							SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
							else -> Alignment.CenterEnd
						}
					)
				)
			}
		}
	) {
		ListItem(
			modifier = modifier,
			leadingContent = {
				CoverArt(
					coverArtId = bookmark.song.coverArtId,
					modifier = Modifier.size(60.dp),
					shape = preferenceManager.coverArtShape.decreasedShape
				)
			},
			content = { Text(bookmark.song.title) },
			supportingContent = {
				Text(
					stringResource(
						R.string.info_bookmark_summary,
						bookmark.song.artists.joinToString { it.name }
							.ifBlank { bookmark.song.artistName ?: "" },
						bookmark.positionMs.milliseconds.toHoursMinutesSeconds(),
						bookmark.createdAt.toReadableDateTime()
					)
				)
			},
			onClick = onClick,
			onLongClick = onClick
		)
	}
}

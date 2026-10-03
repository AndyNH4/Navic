/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.folder.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.di.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.DomainExplicitStatus
import paige.navic.domain.model.DomainSong
import paige.navic.ui.component.common.CoverArt
import paige.navic.ui.component.common.MarqueeText
import paige.navic.ui.navigation.Screen
import paige.navic.ui.util.InlineExplicitIcon
import paige.navic.ui.util.buildSongInfoString
import paige.navic.util.toHoursMinutesSeconds

@Composable
fun FolderSongRow(
	song: DomainSong,
	onClick: () -> Unit
) {
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()

	ListItem(
		onClick = onClick,
		onLongClick = {},
		verticalAlignment = Alignment.CenterVertically,
		content = {
			MarqueeText(
				text = buildAnnotatedString {
					append(song.title)
					if (song.explicitStatus == DomainExplicitStatus.Explicit) {
						append(" ")
						appendInlineContent("InlineExplicitIcon")
					}
				},
				inlineContent = InlineExplicitIcon
			)
		},
		supportingContent = {
			MarqueeText(
				text = buildSongInfoString(
					song = song,
					onClickArtist = { backStack.add(Screen.ArtistDetail(it)) },
					showAlbum = false
				),
				style = MaterialTheme.typography.bodySmall
			)
		},
		leadingContent = {
			CoverArt(
				coverArtId = song.coverArtId,
				modifier = Modifier.size(50.dp),
				shape = preferenceManager.coverArtShape.decreasedShape
			)
		},
		trailingContent = {
			Text(
				text = song.duration.toHoursMinutesSeconds(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1
			)
		}
	)
}

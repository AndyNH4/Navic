/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.folder.component

import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import paige.navic.domain.model.DomainFolder
import paige.navic.icons.Icons
import paige.navic.icons.outlined.ChevronForward
import paige.navic.icons.outlined.Folder

@Composable
fun FolderRow(
	folder: DomainFolder,
	onClick: () -> Unit
) {
	ListItem(
		onClick = onClick,
		onLongClick = {},
		verticalAlignment = Alignment.CenterVertically,
		content = {
			Text(folder.name, maxLines = 1)
		},
		leadingContent = {
			Icon(
				imageVector = Icons.Outlined.Folder,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant
			)
		},
		trailingContent = {
			Icon(
				imageVector = Icons.Outlined.ChevronForward,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	)
}

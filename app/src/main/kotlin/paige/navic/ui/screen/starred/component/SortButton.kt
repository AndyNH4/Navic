/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.starred.component

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.persistentListOf
import paige.navic.domain.model.DomainAlbumListType
import paige.navic.domain.model.DomainArtistListType
import paige.navic.domain.model.DomainSongListType
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Sort
import paige.navic.ui.component.sheet.SortSheet
import paige.navic.ui.util.label

@Composable
fun StarredScreenSortButton(
	selectedSegment: StarredSegment,
	songSorting: DomainSongListType,
	songReversed: Boolean,
	onSetSongSorting: (DomainSongListType) -> Unit,
	onSetSongReversed: (Boolean) -> Unit,
	albumSorting: DomainAlbumListType,
	albumReversed: Boolean,
	onSetAlbumSorting: (DomainAlbumListType) -> Unit,
	onSetAlbumReversed: (Boolean) -> Unit,
	artistSorting: DomainArtistListType,
	onSetArtistSorting: (DomainArtistListType) -> Unit
) {
	val songEntries = remember {
		persistentListOf(
			DomainSongListType.FrequentlyPlayed,
			DomainSongListType.Newest,
			DomainSongListType.Random,
			DomainSongListType.Rating,
			DomainSongListType.Year
		)
	}
	val albumEntries = remember {
		persistentListOf(
			DomainAlbumListType.AlphabeticalByArtist,
			DomainAlbumListType.AlphabeticalByName,
			DomainAlbumListType.Frequent,
			DomainAlbumListType.Recent,
			DomainAlbumListType.Newest,
			DomainAlbumListType.Highest,
			DomainAlbumListType.Random,
			DomainAlbumListType.Year
		)
	}
	val artistEntries = remember {
		persistentListOf(
			DomainArtistListType.AlphabeticalByName,
			DomainArtistListType.Random
		)
	}
	var expanded by remember { mutableStateOf(false) }
	IconButton(onClick = { expanded = true }) {
		Icon(
			imageVector = Icons.Outlined.Sort,
			contentDescription = null
		)
	}
	if (expanded) {
		when (selectedSegment) {
			StarredSegment.Songs -> SortSheet(
				entries = songEntries,
				label = { it.label() },
				selectedSorting = songSorting,
				onSetSorting = onSetSongSorting,
				selectedReversed = songReversed,
				onSetReversed = onSetSongReversed,
				onDismissRequest = { expanded = false }
			)

			StarredSegment.Albums -> SortSheet(
				entries = albumEntries,
				label = { it.label() },
				selectedSorting = albumSorting,
				onSetSorting = onSetAlbumSorting,
				selectedReversed = albumReversed,
				onSetReversed = onSetAlbumReversed,
				onDismissRequest = { expanded = false }
			)

			StarredSegment.Artists -> SortSheet(
				entries = artistEntries,
				label = { stringResource(it.displayName) },
				selectedSorting = artistSorting,
				onSetSorting = onSetArtistSorting,
				onDismissRequest = { expanded = false }
			)
		}
	}
}

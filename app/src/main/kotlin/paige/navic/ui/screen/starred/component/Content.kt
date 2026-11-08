/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.starred.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import paige.navic.R
import paige.navic.data.database.entity.DownloadEntity
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.DomainStarredCollection
import paige.navic.domain.model.settings.ListViewMode
import paige.navic.ui.component.common.ContentUnavailable
import paige.navic.ui.component.layout.ArtGrid
import paige.navic.ui.core.UiState
import paige.navic.ui.screen.album.component.AlbumListScreenGridItem
import paige.navic.ui.screen.album.component.AlbumListScreenListItem
import paige.navic.ui.screen.artist.ArtistListScreenGridItem
import paige.navic.ui.screen.artist.component.ArtistListScreenListItem
import paige.navic.ui.screen.collection.component.CollectionDetailScreenHeadingRowButtons
import paige.navic.ui.util.withoutTop
import paige.navic.util.toSummaryString

@Composable
fun StarredScreenContent(
	innerPadding: PaddingValues,
	onSetShareId: (String) -> Unit,
	isOnline: Boolean = false,
	selectedSegment: StarredSegment,
	onSelectSegment: (StarredSegment) -> Unit,

	songsState: UiState<ImmutableList<DomainSong>>,
	selectedSong: DomainSong?,
	selectedSongIsStarred: Boolean,
	selectedSongRating: Int,
	allDownloads: ImmutableList<DownloadEntity>,
	onSelectSong: (DomainSong) -> Unit,
	onClearSongSelection: () -> Unit,
	onAddSongStar: () -> Unit,
	onRemoveSongStar: () -> Unit,
	onPlaySongNext: (DomainSong) -> Unit,
	onAddSongToQueue: (DomainSong) -> Unit,
	onPlaySong: (Int) -> Unit,
	onSetSongRating: (Int) -> Unit,
	onDownloadSong: (DomainSong) -> Unit,
	onCancelDownloadSong: (DomainSong) -> Unit,
	onDeleteDownloadSong: (DomainSong) -> Unit,

	albumsState: UiState<ImmutableList<DomainAlbum>>,
	selectedAlbum: DomainAlbum?,
	selectedAlbumIsStarred: Boolean,
	selectedAlbumRating: Int,
	albumViewMode: ListViewMode,
	onSelectAlbum: (DomainAlbum) -> Unit,
	onClearAlbumSelection: () -> Unit,
	onStarSelectedAlbum: (Boolean) -> Unit,
	onRateSelectedAlbum: (Int) -> Unit,
	onPlayAlbumNext: () -> Unit,
	onAddAlbumToQueue: () -> Unit,

	artistsState: UiState<ImmutableList<DomainArtist>>,
	selectedArtist: DomainArtist?,
	selectedArtistAlbums: ImmutableList<DomainAlbum>?,
	selectedArtistIsStarred: Boolean,
	artistViewMode: ListViewMode,
	onSelectArtist: (DomainArtist) -> Unit,
	onClearArtistSelection: () -> Unit,
	onStarSelectedArtist: (Boolean) -> Unit,
	onPlayArtistNext: () -> Unit,
	onAddArtistToQueue: () -> Unit,
) {
	val albums = albumsState.data.orEmpty()
	val songs = songsState.data.orEmpty()
	val artists = artistsState.data.orEmpty()

	val songsListState = rememberLazyListState()
	val albumsGridState = rememberLazyGridState()
	val artistsGridState = rememberLazyGridState()

	Column(
		modifier = Modifier.fillMaxSize(),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		if (albums.isEmpty() && songs.isEmpty() && artists.isEmpty()) {
			ContentUnavailable(
				label = stringResource(R.string.info_no_starred)
			)
			return@Column
		}
		SingleChoiceSegmentedButtonRow(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp)
		) {
			StarredSegment.entries.forEachIndexed { index, segment ->
				SegmentedButton(
					shape = SegmentedButtonDefaults.itemShape(
						index = index,
						count = StarredSegment.entries.size
					),
					onClick = { onSelectSegment(segment) },
					selected = selectedSegment == segment,
					label = { Text(stringResource(segment.labelRes)) }
				)
			}
		}
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
		) {
			when (selectedSegment) {
				StarredSegment.Songs -> {
					if (songs.isEmpty()) {
						ContentUnavailable(
							label = stringResource(R.string.info_no_starred_songs)
						)
					} else {
						val totalDuration = remember(songs) {
							songs.fold(Duration.ZERO) { total, song -> total + song.duration }
						}
						val starredCollection = remember(songs) {
							DomainStarredCollection(
								id = "starred-songs",
								name = null,
								coverArtId = songs.firstNotNullOfOrNull { it.coverArtId },
								duration = totalDuration,
								songCount = songs.size,
								songs = songs
							)
						}
						LazyColumn(
							modifier = Modifier.fillMaxSize(),
							state = songsListState,
							contentPadding = innerPadding.withoutTop()
						) {
							item(key = "stats") {
								Text(
									pluralStringResource(
										R.plurals.count_songs,
										songs.size,
										songs.size
									) + " • " + totalDuration.toSummaryString(),
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier
										.heightIn(min = 32.dp)
										.padding(top = 8.dp)
										.padding(horizontal = 16.dp)
										.fillMaxWidth()
								)
							}
							item(key = "buttons") {
								CollectionDetailScreenHeadingRowButtons(starredCollection)
							}
							itemsIndexed(
								items = songs,
								key = { _, song -> song.id }
							) { index, song ->
								val download = allDownloads.find { it.songId == song.id }
								StarredScreenSongRow(
									song = song,
									selected = selectedSong == song,
									onClick = { onPlaySong(index) },
									onLongClick = { onSelectSong(song) },
									onDismissRequest = { onClearSongSelection() },
									starredState = if (selectedSong == song) selectedSongIsStarred else song.starredAt != null,
									onAddStar = onAddSongStar,
									onRemoveStar = onRemoveSongStar,
									download = download,
									onDownload = { onDownloadSong(song) },
									onCancelDownload = { onCancelDownloadSong(song) },
									onDeleteDownload = { onDeleteDownloadSong(song) },
									onPlayNext = { onPlaySongNext(song) },
									onAddToQueue = { onAddSongToQueue(song) },
									onShare = { onSetShareId(song.id) },
									isOnline = isOnline,
									rating = selectedSongRating,
									onSetRating = { onSetSongRating(it) }
								)
							}
						}
					}
				}

				StarredSegment.Albums -> {
					if (albums.isEmpty()) {
						ContentUnavailable(
							label = stringResource(R.string.info_no_starred_albums)
						)
					} else {
						ArtGrid(
							state = albumsGridState,
							contentPadding = innerPadding.withoutTop(),
							selectedViewMode = albumViewMode,
							verticalArrangement = Arrangement.spacedBy(
								if (albumViewMode == ListViewMode.List) 0.dp else 12.dp
							)
						) {
							items(albums, key = { it.id }) { album ->
								if (albumViewMode == ListViewMode.Grid) {
									AlbumListScreenGridItem(
										modifier = Modifier.animateItem(),
										tab = "starred",
										album = album,
										selected = album == selectedAlbum,
										starred = selectedAlbumIsStarred,
										rating = selectedAlbumRating,
										onSelect = { onSelectAlbum(album) },
										onDeselect = onClearAlbumSelection,
										onSetStarred = onStarSelectedAlbum,
										onSetShareId = onSetShareId,
										onPlayNext = onPlayAlbumNext,
										onAddToQueue = onAddAlbumToQueue,
										onSetRating = onRateSelectedAlbum
									)
								} else {
									AlbumListScreenListItem(
										modifier = Modifier.animateItem(),
										album = album,
										selected = album == selectedAlbum,
										starred = selectedAlbumIsStarred,
										rating = selectedAlbumRating,
										onSelect = { onSelectAlbum(album) },
										onDeselect = onClearAlbumSelection,
										onSetStarred = onStarSelectedAlbum,
										onSetShareId = onSetShareId,
										onPlayNext = onPlayAlbumNext,
										onAddToQueue = onAddAlbumToQueue,
										onSetRating = onRateSelectedAlbum
									)
								}
							}
						}
					}
				}

				StarredSegment.Artists -> {
					if (artists.isEmpty()) {
						ContentUnavailable(
							label = stringResource(R.string.info_no_starred_artists)
						)
					} else {
						ArtGrid(
							state = artistsGridState,
							contentPadding = innerPadding.withoutTop(),
							selectedViewMode = artistViewMode,
							verticalArrangement = Arrangement.spacedBy(
								if (artistViewMode == ListViewMode.List) 0.dp else 12.dp
							)
						) {
							items(artists, key = { it.id }) { artist ->
								if (artistViewMode == ListViewMode.Grid) {
									ArtistListScreenGridItem(
										modifier = Modifier.animateItem(),
										tab = "starred",
										artist = artist,
										selected = artist == selectedArtist,
										selectedArtistAlbums = selectedArtistAlbums,
										starred = selectedArtistIsStarred,
										onSelect = { onSelectArtist(artist) },
										onDeselect = onClearArtistSelection,
										onPlayNext = onPlayArtistNext,
										onAddToQueue = onAddArtistToQueue,
										onSetStarred = onStarSelectedArtist
									)
								} else {
									ArtistListScreenListItem(
										modifier = Modifier.animateItem(),
										artist = artist,
										selected = artist == selectedArtist,
										selectedArtistAlbums = selectedArtistAlbums,
										starred = selectedArtistIsStarred,
										onSelect = { onSelectArtist(artist) },
										onDeselect = onClearArtistSelection,
										onPlayNext = onPlayArtistNext,
										onAddToQueue = onAddArtistToQueue,
										onSetStarred = onStarSelectedArtist
									)
								}
							}
						}
					}
				}
			}
		}
	}
}

enum class StarredSegment(val labelRes: Int) {
	Songs(R.string.title_songs),
	Albums(R.string.title_albums),
	Artists(R.string.title_artists)
}

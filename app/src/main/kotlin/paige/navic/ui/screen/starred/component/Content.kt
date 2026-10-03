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
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.data.database.entity.DownloadEntity
import paige.navic.data.database.entity.DownloadStatus
import paige.navic.di.LocalNavStack
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.DomainStarredCollection
import paige.navic.ui.component.common.ContentUnavailable
import paige.navic.ui.component.common.SongRow
import paige.navic.ui.component.layout.ArtCarouselItem
import paige.navic.ui.component.sheet.ArtistSheet
import paige.navic.ui.component.sheet.CollectionSheet
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.collection.component.CollectionDetailScreenHeadingRowButtons
import paige.navic.ui.screen.playlist.dialog.PlaylistUpdateDialog
import paige.navic.ui.util.withoutTop
import paige.navic.util.toSummaryString

@Composable
fun StarredScreenContent(
	innerPadding: PaddingValues,
	onSetShareId: (String) -> Unit,
	isOnline: Boolean = false,

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

	// albums
	albumsState: UiState<ImmutableList<DomainAlbum>>,
	selectedAlbum: DomainAlbum?,
	selectedAlbumIsStarred: Boolean,
	selectedAlbumRating: Int,
	onSelectAlbum: (DomainAlbum) -> Unit,
	onClearAlbumSelection: () -> Unit,
	onStarSelectedAlbum: (Boolean) -> Unit,
	onRateSelectedAlbum: (Int) -> Unit,
	onPlayAlbumNext: () -> Unit,
	onAddAlbumToQueue: () -> Unit,

	// artists
	artistsState: UiState<ImmutableList<DomainArtist>>,
	selectedArtist: DomainArtist?,
	selectedArtistAlbums: ImmutableList<DomainAlbum>?,
	selectedArtistIsStarred: Boolean,
	onSelectArtist: (DomainArtist) -> Unit,
	onClearArtistSelection: () -> Unit,
	onStarSelectedArtist: (Boolean) -> Unit,
	onPlayArtistNext: () -> Unit,
	onAddArtistToQueue: () -> Unit,
) {
	val backStack = LocalNavStack.current
	val albums = albumsState.data.orEmpty()
	val songs = songsState.data.orEmpty()
	val artists = artistsState.data.orEmpty()
	val downloadManager = koinInject<DownloadManager>()

	val scope = rememberCoroutineScope()

	var songsToAddToPlaylist by rememberSaveable { mutableStateOf<ImmutableList<DomainSong>?>(null) }

	var selectedSegment by rememberSaveable { mutableStateOf(StarredSegment.Songs) }

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
					onClick = { selectedSegment = segment },
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
								SongRow(
									modifier = Modifier.fillMaxWidth(),
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
						LazyVerticalGrid(
							columns = GridCells.Fixed(2),
							modifier = Modifier.fillMaxSize(),
							state = albumsGridState,
							contentPadding = innerPadding.withoutTop() + PaddingValues(
								start = 16.dp,
								top = 16.dp,
								end = 16.dp
							),
							horizontalArrangement = Arrangement.spacedBy(12.dp),
							verticalArrangement = Arrangement.spacedBy(12.dp)
						) {
							items(
								items = albums,
								key = { it.id }
							) { album ->
								val albumDownloadStatus by downloadManager
									.getCollectionDownloadStatus(album.songs.map { it.id })
									.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)
								ArtCarouselItem(
									coverArtId = album.coverArtId,
									title = album.name ?: "[unknown album]",
									subtitle = album.artistName,
									contentDescription = null,
									onSelect = { onSelectAlbum(album) },
									onClick = dropUnlessResumed {
										backStack.add(Screen.CollectionDetail(album.id, "artist"))
									}
								)
								if (selectedAlbum == album) {
									CollectionSheet(
										onDismissRequest = { onClearAlbumSelection() },
										collection = album,
										starred = selectedAlbumIsStarred,
										onShare = { onSetShareId(album.id) },
										onPlayNext = onPlayAlbumNext,
										onAddToQueue = onAddAlbumToQueue,
										onSetStarred = { onStarSelectedAlbum(!selectedAlbumIsStarred) },
										onAddAllToPlaylist = {
											songsToAddToPlaylist = selectedAlbum.songs.toImmutableList()
										},
										downloadStatus = albumDownloadStatus,
										onDownloadAll = {
											scope.launch {
												downloadManager.downloadCollection(album)
											}
										},
										onCancelDownloadAll = {
											scope.launch {
												album.songs.forEach { downloadManager.cancelDownload(it.id) }
											}
										},
										onDeleteDownloadAll = {
											scope.launch {
												downloadManager.deleteDownloadedCollection(album)
											}
										},
										rating = selectedAlbumRating,
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
						LazyVerticalGrid(
							columns = GridCells.Fixed(2),
							modifier = Modifier.fillMaxSize(),
							state = artistsGridState,
							contentPadding = innerPadding.withoutTop() + PaddingValues(
								start = 16.dp,
								top = 16.dp,
								end = 16.dp
							),
							horizontalArrangement = Arrangement.spacedBy(12.dp),
							verticalArrangement = Arrangement.spacedBy(12.dp)
						) {
							items(
								items = artists,
								key = { it.id }
							) { artist ->
								ArtCarouselItem(
									coverArtId = artist.coverArtId,
									title = artist.name,
									subtitle = pluralStringResource(
										R.plurals.count_albums,
										artist.albumCount,
										artist.albumCount
									),
									contentDescription = null,
									onSelect = { onSelectArtist(artist) },
									onClick = dropUnlessResumed {
										backStack.add(Screen.ArtistDetail(artist.id))
									}
								)
								if (selectedArtist == artist) {
									ArtistSheet(
										onDismissRequest = onClearArtistSelection,
										artist = artist,
										onPlayNext = onPlayArtistNext,
										onAddToQueue = onAddArtistToQueue,
										onAddAllToPlaylist = {
											songsToAddToPlaylist =
												selectedArtistAlbums?.flatMap { it.songs }.orEmpty()
													.toImmutableList()
										},
										starred = selectedArtistIsStarred,
										onSetStarred = { onStarSelectedArtist(!selectedArtistIsStarred) }
									)
								}
							}
						}
					}
				}
			}
		}
	}

	songsToAddToPlaylist?.let {
		PlaylistUpdateDialog(
			songs = it,
			onDismissRequest = { songsToAddToPlaylist = null }
		)
	}
}

private enum class StarredSegment(val labelRes: Int) {
	Songs(R.string.title_songs),
	Albums(R.string.title_albums),
	Artists(R.string.title_artists)
}

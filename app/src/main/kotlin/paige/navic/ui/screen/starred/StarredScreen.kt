/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.starred

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.R
import paige.navic.di.LocalBottomBarScrollManager
import paige.navic.di.LocalSizeClass
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.DomainFilter
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.DomainSongCollection
import paige.navic.domain.model.settings.BottomBarVisibilityMode
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.component.dialog.QueueDuplicateDialog
import paige.navic.ui.component.layout.PullToRefreshBox
import paige.navic.ui.component.layout.RootBottomBar
import paige.navic.ui.component.layout.RootTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.PersistentViewModelStoreOwner
import paige.navic.ui.screen.album.viewmodel.AlbumListViewModel
import paige.navic.ui.screen.artist.viewmodel.ArtistListViewModel
import paige.navic.ui.screen.share.dialog.ShareDialog
import paige.navic.ui.screen.song.viewmodel.SongListViewModel
import paige.navic.ui.screen.starred.component.StarredScreenContent
import paige.navic.ui.screen.starred.component.StarredScreenSortButton
import paige.navic.ui.screen.starred.component.StarredSegment
import kotlin.time.Duration

@Composable
fun StarredScreen() {
	val persistentViewModelStoreOwner = koinInject<PersistentViewModelStoreOwner>()
	val preferenceManager = koinInject<PreferenceManager>()

	val songsViewModel = koinViewModel<SongListViewModel>(
		key = "starredSongs",
		parameters = {
			parametersOf(
				preferenceManager.starredSongSortType,
				setOf(DomainFilter.Starred),
				preferenceManager.starredSongSortReversed
			)
		},
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val songsState by songsViewModel.songsState.collectAsStateWithLifecycle()
	val selectedSong by songsViewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSongIsStarred by songsViewModel.starred.collectAsStateWithLifecycle()
	val selectedSongRating by songsViewModel.selectedSongRating.collectAsStateWithLifecycle()
	val allDownloads by songsViewModel.allDownloads.collectAsStateWithLifecycle()
	val songsSorting by songsViewModel.selectedSorting.collectAsStateWithLifecycle()
	val songsReversed by songsViewModel.selectedReversed.collectAsStateWithLifecycle()

	val albumsViewModel = koinViewModel<AlbumListViewModel>(
		key = "starredAlbums",
		parameters = {
			parametersOf(
				preferenceManager.starredAlbumSortType,
				setOf(DomainFilter.Starred),
				preferenceManager.starredAlbumSortReversed
			)
		},
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val albumsState by albumsViewModel.albumsState.collectAsStateWithLifecycle()
	val selectedAlbum by albumsViewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by albumsViewModel.starred.collectAsStateWithLifecycle()
	val selectedAlbumRating by albumsViewModel.rating.collectAsStateWithLifecycle()
	val albumsSorting by albumsViewModel.listType.collectAsStateWithLifecycle()
	val albumsReversed by albumsViewModel.selectedReversed.collectAsStateWithLifecycle()

	val artistsViewModel = koinViewModel<ArtistListViewModel>(
		key = "starredArtists",
		parameters = {
			parametersOf(
				preferenceManager.starredArtistSortType,
				setOf(DomainFilter.Starred)
			)
		},
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val artistsState by artistsViewModel.artistsState.collectAsStateWithLifecycle()
	val selectedArtist by artistsViewModel.selectedArtist.collectAsStateWithLifecycle()
	val selectedArtistAlbums by artistsViewModel.selectedArtistAlbums.collectAsStateWithLifecycle()
	val selectedArtistIsStarred by artistsViewModel.starred.collectAsStateWithLifecycle()
	val artistsSorting by artistsViewModel.listType.collectAsStateWithLifecycle()

	var selectedSegment by rememberSaveable { mutableStateOf(StarredSegment.Songs) }

	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	val player = koinInject<MediaPlayerViewModel>()

	var songToQueue by remember { mutableStateOf<DomainSong?>(null) }

	val isOnline by songsViewModel.isOnline.collectAsStateWithLifecycle()

	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	Scaffold(
		topBar = {
			RootTopBar(
				{ Text(stringResource(R.string.title_starred)) },
				scrollBehavior,
				actions = {
					StarredScreenSortButton(
						selectedSegment = selectedSegment,
						songSorting = songsSorting,
						songReversed = songsReversed,
						onSetSongSorting = {
							songsViewModel.setSorting(it, persist = false)
							preferenceManager.starredSongSortType = it
						},
						onSetSongReversed = {
							songsViewModel.setReversed(it, persist = false)
							preferenceManager.starredSongSortReversed = it
						},
						albumSorting = albumsSorting,
						albumReversed = albumsReversed,
						onSetAlbumSorting = {
							albumsViewModel.setListType(it, persist = false)
							preferenceManager.starredAlbumSortType = it
						},
						onSetAlbumReversed = {
							albumsViewModel.setReversed(it, persist = false)
							preferenceManager.starredAlbumSortReversed = it
						},
						artistSorting = artistsSorting,
						onSetArtistSorting = {
							artistsViewModel.setListType(it, persist = false)
							preferenceManager.starredArtistSortType = it
						}
					)
				}
			)
		},
		bottomBar = {
			val sizeClass = LocalSizeClass.current
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible =
				preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (sizeClass.widthSizeClass < WindowWidthSizeClass.Medium && preferVisible) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		val isAnythingLoading = albumsState is UiState.Loading ||
			artistsState is UiState.Loading ||
			songsState is UiState.Loading
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface)
				.nestedScroll(scrollBehavior.nestedScrollConnection),
			finished = !isAnythingLoading,
			onRefresh = {
				albumsViewModel.refreshAlbums(true)
				artistsViewModel.refreshArtists(true)
				songsViewModel.refreshSongs(true)
			},
			key = listOf(albumsState, artistsState, songsState)
		) {
			StarredScreenContent(
				innerPadding = innerPadding,
				onSetShareId = { shareId = it },
				isOnline = isOnline,
				selectedSegment = selectedSegment,
				onSelectSegment = { selectedSegment = it },

				songsState = songsState,
				selectedSong = selectedSong,
				allDownloads = allDownloads,
				onPlaySong = { index ->
					player.playNow(songsState.data.orEmpty(), index)
				},
				onSelectSong = {
					songsViewModel.selectSong(it)
				},
				onClearSongSelection = { songsViewModel.clearSelection() },
				selectedSongIsStarred = selectedSongIsStarred,
				onAddSongStar = { songsViewModel.starSong(true) },
				onRemoveSongStar = { songsViewModel.starSong(false) },
				onDownloadSong = { songsViewModel.downloadSong(it) },
				onCancelDownloadSong = { song ->
					songsViewModel.cancelDownload(song.id)
				},
				onDeleteDownloadSong = { song ->
					songsViewModel.deleteDownload(song.id)
				},
				onPlaySongNext = { song ->
					if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
						songToQueue = song
					} else {
						player.playNextSingle(song)
					}
				},
				onAddSongToQueue = { song ->
					if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
						songToQueue = song
					} else {
						player.addToQueueSingle(song)
					}
				},
				selectedSongRating = selectedSongRating,
				onSetSongRating = { songsViewModel.rateSelectedSong(it) },

				albumsState = albumsState,
				selectedAlbum = selectedAlbum,
				selectedAlbumIsStarred = selectedAlbumIsStarred,
				selectedAlbumRating = selectedAlbumRating,
				onSelectAlbum = { albumsViewModel.selectAlbum(it) },
				onClearAlbumSelection = { albumsViewModel.clearSelection() },
				onStarSelectedAlbum = { albumsViewModel.starAlbum(it) },
				onPlayAlbumNext = { if (selectedAlbum != null) player.playNext(selectedAlbum as DomainSongCollection) },
				onAddAlbumToQueue = { if (selectedAlbum != null) player.addToQueue(selectedAlbum as DomainSongCollection) },
				onRateSelectedAlbum = { albumsViewModel.setRating(it) },

				artistsState = artistsState,
				selectedArtist = selectedArtist,
				selectedArtistAlbums = selectedArtistAlbums,
				selectedArtistIsStarred = selectedArtistIsStarred,
				onSelectArtist = { artistsViewModel.selectArtist(it) },
				onClearArtistSelection = { artistsViewModel.clearSelection() },
				onStarSelectedArtist = { artistsViewModel.starArtist(it) },
				onPlayArtistNext = {
					if (selectedArtist != null) artistsViewModel.playArtistAlbumsNext(
						player
					)
				},
				onAddArtistToQueue = {
					if (selectedArtist != null) artistsViewModel.addArtistAlbumsToQueue(
						player
					)
				},
			)
		}
	}

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

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.artist.viewmodel

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import paige.navic.R
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.entity.ArtistGroupEntity
import paige.navic.data.database.entity.ArtistGroupMemberEntity
import paige.navic.data.database.entity.DownloadStatus
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.domain.manager.ArtistGroupManager
import paige.navic.domain.manager.ArtistNameResolver
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.model.DomainSong
import paige.navic.domain.repository.AlbumRepository
import paige.navic.domain.repository.ArtistRepository
import paige.navic.domain.repository.DbRepository
import paige.navic.domain.repository.SongRepository
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.core.UiState
import paige.navic.util.Logger

@Immutable
data class ArtistState(
	val artist: DomainArtist,
	val albums: List<DomainAlbum>,
	val topSongs: List<DomainSong>,
	val participationSongs: List<DomainSong> = emptyList(),
	val similarArtists: List<DomainArtist> = emptyList(),
	val group: ArtistGroupEntity? = null,
	val groupMembers: List<ArtistGroupMemberEntity> = emptyList()
)

class ArtistDetailViewModel(
	private val artistId: String,
	private val repository: DbRepository,
	private val artistRepository: ArtistRepository,
	private val songRepository: SongRepository,
	private val albumRepository: AlbumRepository,
	private val artistDao: ArtistDao,
	private val albumDao: AlbumDao,
	private val songDao: SongDao,
	private val downloadManager: DownloadManager,
	private val snackBarManager: SnackBarManager,
	private val artistGroupManager: ArtistGroupManager,
	connectivityManager: ConnectivityManager,
	private val context: Context
) : ViewModel() {
	val artistState: StateFlow<UiState<ArtistState>>
		field = MutableStateFlow<UiState<ArtistState>>(UiState.Loading())

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val mergeCandidates: StateFlow<List<DomainArtist>>
		field = MutableStateFlow(emptyList())

	private var mergeSearchJob: Job? = null

	val selectedSong: StateFlow<DomainSong?>
		field = MutableStateFlow(null)

	val selectedSongIsStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	val selectedAlbum: StateFlow<DomainAlbum?>
		field = MutableStateFlow(null)

	val selectedAlbumIsStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedAlbumRating: StateFlow<Int>
		field = MutableStateFlow(0)

	val isOnline = connectivityManager.isOnline

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptyList()
		)

	val scrollState = ScrollState(initial = 0)

	init {
		loadArtistData()
	}

	private fun loadArtistData() {
		viewModelScope.launch {
			try {
				val groupInfo = artistGroupManager.getGroupInfo(artistId)
				val groupMembers = groupInfo?.members.orEmpty()
				val memberIds = groupMembers.map { it.artistId }.ifEmpty { listOf(artistId) }
				val primaryArtistId = groupInfo?.group?.primaryArtistId ?: artistId
				val participantSongEntities = memberIds
					.flatMap { songDao.getSongsByParticipant(it) }
					.distinctBy { it.songId }
				val artistEntity = artistDao.getArtistById(primaryArtistId)
				val baseArtist = artistEntity?.toDomainModel() ?: run {
					val participantName = participantSongEntities
						.flatMap { it.artists }
						.firstOrNull { it.id == primaryArtistId }
						?.name
						?.takeIf { it.isNotBlank() }

					if (participantName != null) {
						DomainArtist(
							id = primaryArtistId,
							name = participantName,
							isTrackArtist = true
						)
					} else {
						repository.fetchArtist(primaryArtistId).getOrNull()
					}
				} ?: throw IllegalStateException(
					context.getString(R.string.error_artist_unavailable)
				)
				val groupArtists = memberIds.mapNotNull { artistDao.getArtistById(it)?.toDomainModel() }
				val domainArtist = if (groupInfo == null) {
					baseArtist
				} else {
					baseArtist.copy(
						name = ArtistNameResolver.resolveDisplayName(
							baseArtist,
							groupMembers,
							groupInfo.group
						),
						albumCount = if (groupArtists.isEmpty()) {
							baseArtist.albumCount
						} else {
							groupArtists.sumOf { it.albumCount }
						},
						starredAt = groupArtists.mapNotNull { it.starredAt }.maxOrNull()
					)
				}

				val albumsWithSongs = memberIds.map { memberId ->
					val artistAlbums = albumDao.getAlbumsByArtist(memberId).firstOrNull().orEmpty()
					if (artistAlbums.isNotEmpty()) {
						artistAlbums
					} else {
						val memberName = groupMembers.firstOrNull { it.artistId == memberId }?.name
							?: baseArtist.name
						albumDao.getAlbumsByArtistName(memberName).firstOrNull().orEmpty()
					}
				}.flatten().distinctBy { it.album.albumId }

				val domainAlbums = albumsWithSongs.map { it.toDomainModel() }
				val ownSongEntities = albumsWithSongs.flatMap { it.songs }.distinctBy { it.songId }
				val ownSongIds = ownSongEntities.map { it.songId }.toSet()

				val domainSongs = ownSongEntities
					.map { it.toDomainModel() }
					.sortedByDescending { it.playCount }
					.take(12)
				val participationSongs = participantSongEntities
					.filter { it.songId !in ownSongIds }
					.map { it.toDomainModel() }
					.sortedByDescending { it.playCount }
					.take(12)

				val initialSimilarArtists = artistGroupManager.collapseArtists(
					domainArtist.similarArtistIds.mapNotNull { id ->
						artistDao.getArtistById(id)?.toDomainModel()
					}
				)

				starred.value = artistRepository.isArtistStarred(domainArtist)

				artistState.value = UiState.Success(
					ArtistState(
						artist = domainArtist,
						albums = domainAlbums,
						topSongs = domainSongs,
						participationSongs = participationSongs,
						similarArtists = initialSimilarArtists,
						group = groupInfo?.group,
						groupMembers = groupMembers
					)
				)

				repository.fetchArtistMetadata(primaryArtistId)
					.onSuccess { updatedArtist ->
						val currentState = (artistState.value as? UiState.Success)?.data
						if (currentState != null) {
							val updatedSimilarArtists = artistGroupManager.collapseArtists(
								updatedArtist.similarArtistIds.mapNotNull { id ->
									artistDao.getArtistById(id)?.toDomainModel()
								}
							)
							val displayArtist = if (groupInfo == null) {
								updatedArtist
							} else {
								updatedArtist.copy(
									name = ArtistNameResolver.resolveDisplayName(
										updatedArtist,
										groupMembers,
										groupInfo.group
									),
									albumCount = currentState.artist.albumCount,
									starredAt = currentState.artist.starredAt
								)
							}

							artistState.value = UiState.Success(
								currentState.copy(
									artist = displayArtist,
									similarArtists = updatedSimilarArtists
								)
							)
						}
					}
					.onFailure { error ->
						Logger.e("ArtistDetailViewModel", "Failed to fetch artist metadata", error)
					}
			} catch (e: Exception) {
				artistState.value = UiState.Error(e)
			}
		}
	}

	fun selectSong(song: DomainSong) {
		viewModelScope.launch {
			selectedSong.value = song
			selectedSongIsStarred.value = songRepository.isSongStarred(song)
			selectedSongRating.value = songRepository.getSongRating(song)
		}
	}

	fun clearSelection() {
		selectedSong.value = null
	}

	fun selectAlbum(album: DomainAlbum) {
		viewModelScope.launch {
			selectedAlbum.value = album
			selectedAlbumIsStarred.value = albumRepository.isAlbumStarred(album)
			selectedAlbumRating.value = albumRepository.getAlbumRating(album)
		}
	}

	fun rateSelectedAlbum(rating: Int) {
		viewModelScope.launch {
			val selection = selectedAlbum.value ?: return@launch
			runCatching {
				selectedAlbumRating.value = rating
				albumRepository.rateAlbum(selection, rating)
			}
		}
	}

	fun clearAlbumSelection() {
		selectedAlbum.value = null
	}

	fun starSelectedSong() {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				selectedSongIsStarred.value = true
				songRepository.starSong(selection)
				loadArtistData()
			}
		}
	}

	fun unstarSelectedSong() {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				selectedSongIsStarred.value = false
				songRepository.unstarSong(selection)
				loadArtistData()
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				selectedSongRating.value = rating
				songRepository.rateSong(selection, rating)
			}
		}
	}

	fun starArtist(isStarred: Boolean) {
		val artist = (artistState.value as? UiState.Success)?.data?.artist ?: return
		viewModelScope.launch {
			runCatching {
				if (isStarred) {
					artistRepository.starArtist(artist)
				} else {
					artistRepository.unstarArtist(artist)
				}
				starred.value = artistRepository.isArtistStarred(artist)
			}
		}
	}

	fun searchMergeCandidates(query: String) {
		mergeSearchJob?.cancel()
		mergeSearchJob = viewModelScope.launch {
			val state = (artistState.value as? UiState.Success)?.data ?: return@launch
			val excludedIds = buildSet {
				add(artistId)
				add(state.artist.id)
				addAll(state.groupMembers.map { it.artistId })
			}
			val matchingArtists = artistDao.getArtistsAlphabeticalByName()
				.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
				.map { it.toDomainModel() }
			mergeCandidates.value = artistGroupManager.collapseArtists(matchingArtists)
				.filter { it.id !in excludedIds }
				.take(50)
		}
	}

	fun mergeWithArtist(otherArtistId: String) {
		val primaryArtistId = (artistState.value as? UiState.Success)?.data?.artist?.id ?: artistId
		viewModelScope.launch {
			runCatching { artistGroupManager.mergeArtists(primaryArtistId, otherArtistId) }
				.onSuccess { loadArtistData() }
				.onFailure { snackBarManager.notify(R.string.error_artist_group_action_failed) }
		}
	}

	fun setPreferredName(name: String) {
		val groupKey = (artistState.value as? UiState.Success)?.data?.group?.groupKey ?: return
		viewModelScope.launch {
			runCatching { artistGroupManager.setPreferredName(groupKey, name) }
				.onSuccess { loadArtistData() }
				.onFailure { snackBarManager.notify(R.string.error_artist_group_action_failed) }
		}
	}

	fun removeFromGroup() {
		viewModelScope.launch {
			runCatching { artistGroupManager.removeFromGroup(artistId) }
				.onSuccess { loadArtistData() }
				.onFailure { snackBarManager.notify(R.string.error_artist_group_action_failed) }
		}
	}

	fun disbandGroup() {
		val groupKey = (artistState.value as? UiState.Success)?.data?.group?.groupKey ?: return
		viewModelScope.launch {
			runCatching { artistGroupManager.disbandGroup(groupKey) }
				.onSuccess { loadArtistData() }
				.onFailure { snackBarManager.notify(R.string.error_artist_group_action_failed) }
		}
	}

	fun starAlbum(starred: Boolean) {
		viewModelScope.launch {
			val selection = selectedAlbum.value ?: return@launch
			runCatching {
				if (starred) {
					albumRepository.starAlbum(selection)
				} else {
					albumRepository.unstarAlbum(selection)
				}
				selectedAlbumIsStarred.value = starred
			}
		}
	}

	fun playArtistAlbums(player: MediaPlayerViewModel) {
		val songs = (artistState.value as? UiState.Success)?.data
			?.albums?.flatMap { it.songs }
			?: return

		if (songs.isEmpty()) return
		player.playNow(songs)
	}

	fun playArtistAlbumsShuffled(player: MediaPlayerViewModel) {
		val songs = (artistState.value as? UiState.Success)?.data
			?.albums?.flatMap { it.songs }
			?: return

		if (songs.isEmpty()) return
		player.playNow(songs.shuffled())
	}

	fun downloadSong(song: DomainSong) {
		downloadManager.downloadSong(song)
		snackBarManager.notify(R.string.notice_download_started)
	}

	fun cancelDownload(songId: String) {
		downloadManager.cancelDownload(songId)
	}

	fun deleteDownload(songId: String) {
		downloadManager.deleteDownload(songId)
		snackBarManager.notify(R.string.notice_deleted_download)
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	fun collectionDownloadStatus(): Flow<DownloadStatus> {
		return artistState.flatMapLatest { state ->
			if (state is UiState.Success) {
				val allArtistSongIds = state.data.albums.flatMap { album ->
					album.songs.map { it.id }
				}

				if (allArtistSongIds.isEmpty()) {
					flowOf(DownloadStatus.NOT_DOWNLOADED)
				} else {
					downloadManager.getCollectionDownloadStatus(allArtistSongIds)
				}
			} else {
				flowOf(DownloadStatus.NOT_DOWNLOADED)
			}
		}
	}
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.entity.DownloadStatus
import paige.navic.data.database.entity.SyncActionType
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.data.database.mapper.toEntity
import paige.navic.domain.manager.ArtistGroupManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.model.DomainArtistListType
import paige.navic.domain.model.DomainFilter
import paige.navic.domain.model.DomainSong
import paige.navic.ui.core.UiState
import kotlin.time.Clock

class ArtistRepository(
	private val artistDao: ArtistDao,
	private val albumDao: AlbumDao,
	private val songDao: SongDao,
	private val downloadDao: DownloadDao,
	private val syncManager: SyncManager,
	private val dbRepository: DbRepository,
	private val preferenceManager: PreferenceManager,
	private val artistGroupManager: ArtistGroupManager
) {
	private suspend fun getLocalData(
		listType: DomainArtistListType,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainArtist> {
		val artists = when (listType) {
			DomainArtistListType.AlphabeticalByName -> artistDao.getArtistsAlphabeticalByName()
			DomainArtistListType.Random -> artistDao.getArtistsRandom()
		}
			.map { it.toDomainModel() }
			.let { artistGroupManager.collapseArtists(it) }
			.filter { preferenceManager.showTrackArtists || !it.isTrackArtist }

		return artists.filter { artist ->
			filters.all { filter ->
				when (filter) {
					DomainFilter.Starred -> artist.starredAt != null
					DomainFilter.Downloaded -> {
						val artistSongs = getArtistSongs(artist)
						artistSongs.isNotEmpty() && artistSongs.all { song ->
							downloadDao.getDownloadById(song.id)?.status == DownloadStatus.DOWNLOADED
						}
					}
				}
			}
		}.toImmutableList()
	}

	private suspend fun refreshLocalData(
		listType: DomainArtistListType,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainArtist> {
		dbRepository.syncArtists().getOrThrow()
		return getLocalData(listType, filters)
	}

	fun getArtistsFlow(
		fullRefresh: Boolean,
		listType: DomainArtistListType,
		filters: Set<DomainFilter> = emptySet()
	): Flow<UiState<ImmutableList<DomainArtist>>> = flow {
		val localData = getLocalData(listType, filters)
		if (fullRefresh) {
			emit(UiState.Loading(data = localData))
			try {
				emit(UiState.Success(data = refreshLocalData(listType, filters)))
			} catch (error: Exception) {
				emit(UiState.Error(error = error, data = localData))
			}
		} else {
			emit(UiState.Success(data = localData))
		}
	}.flowOn(Dispatchers.IO)

	suspend fun getArtistAlbums(artist: DomainArtist): List<DomainAlbum> {
		val groupInfo = artistGroupManager.getGroupInfo(artist.id)
		val members = groupInfo?.members.orEmpty()
		val memberIds = members.map { it.artistId }.ifEmpty { listOf(artist.id) }
		return memberIds.flatMap { memberId ->
			val artistAlbums = albumDao.getAlbumsByArtist(memberId).firstOrNull().orEmpty()
			if (artistAlbums.isNotEmpty()) {
				artistAlbums
			} else {
				val memberName = members.firstOrNull { it.artistId == memberId }?.name
					?: artist.name
				albumDao.getAlbumsByArtistName(memberName).firstOrNull().orEmpty()
			}
		}.distinctBy { it.album.albumId }
			.map { it.toDomainModel() }
	}

	suspend fun getArtistSongs(artist: DomainArtist): List<DomainSong> {
		return getMemberIds(artist.id).flatMap { memberId ->
			songDao.getSongsByArtistId(memberId) + songDao.getSongsByParticipant(memberId)
		}.distinctBy { it.songId }
			.map { it.toDomainModel() }
	}

	suspend fun isArtistStarred(artist: DomainArtist): Boolean {
		return getMemberIds(artist.id).any { artistDao.isArtistStarred(it) }
	}

	suspend fun starArtist(artist: DomainArtist) {
		setArtistStarred(artist, true)
	}

	suspend fun unstarArtist(artist: DomainArtist) {
		setArtistStarred(artist, false)
	}

	private suspend fun setArtistStarred(artist: DomainArtist, starred: Boolean) {
		val starredAt = if (starred) Clock.System.now() else null
		val action = if (starred) SyncActionType.STAR else SyncActionType.UNSTAR
		val memberIds = getMemberIds(artist.id)
		if (artistDao.getArtistById(artist.id) == null) {
			artistDao.insertArtist(artist.toEntity())
		}
		artistDao.updateStarredStates(memberIds, starredAt)
		memberIds.forEach { artistId ->
			syncManager.enqueueAction(action, artistId)
		}
	}

	private suspend fun getMemberIds(artistId: String): List<String> {
		return artistGroupManager.getGroupInfo(artistId)
			?.members?.map { it.artistId }
			?.ifEmpty { listOf(artistId) }
			?: listOf(artistId)
	}
}

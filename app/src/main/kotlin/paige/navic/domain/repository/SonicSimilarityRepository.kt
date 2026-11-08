/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.dao.SonicSimilarityDao
import paige.navic.data.database.entity.SongEntity
import paige.navic.data.database.entity.SonicSimilarityEntity
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.data.database.mapper.toEntity
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.model.DomainSong
import kotlin.time.Clock

class SonicSimilarityCacheMissException : Exception()

data class SonicSimilarityResult(
	val songs: List<DomainSong>,
	val fromCache: Boolean,
	val updatedAt: Long?
)

class SonicSimilarityRepository(
	private val sessionManager: SessionManager,
	private val connectivityManager: ConnectivityManager,
	private val songDao: SongDao,
	private val sonicSimilarityDao: SonicSimilarityDao
) {
	val cachedSeedCount: Flow<Int> = sonicSimilarityDao.getCachedSeedCount()
	val cachedRelationCount: Flow<Int> = sonicSimilarityDao.getCachedRelationCount()

	suspend fun getSimilarSongs(
		songId: String,
		count: Int = 30
	): SonicSimilarityResult = withContext(Dispatchers.IO) {
		if (!connectivityManager.isOnline.value) {
			return@withContext readCache(songId)
				?: throw SonicSimilarityCacheMissException()
		}

		try {
			val remoteSongs = fetchRemoteSongs(songId, count)
			if (remoteSongs.isNotEmpty()) {
				val updatedAt = cacheRemoteSongs(songId, remoteSongs)
				return@withContext SonicSimilarityResult(
					songs = remoteSongs.map { it.toDomainModel() },
					fromCache = false,
					updatedAt = updatedAt
				)
			}
		} catch (error: CancellationException) {
			throw error
		} catch (error: Exception) {
			readCache(songId)?.let { return@withContext it }
			throw error
		}

		readCache(songId) ?: SonicSimilarityResult(
			songs = emptyList(),
			fromCache = false,
			updatedAt = null
		)
	}

	suspend fun refreshSimilarSongsCache(songId: String, count: Int = 10): Int =
		withContext(Dispatchers.IO) {
			val remoteSongs = fetchRemoteSongs(songId, count)
			if (remoteSongs.isEmpty()) return@withContext 0
			cacheRemoteSongs(songId, remoteSongs)
			remoteSongs.size
		}

	suspend fun getCachedSeedIds(): List<String> = sonicSimilarityDao.getCachedSeedIds()

	suspend fun clearCache() = sonicSimilarityDao.clearAll()

	private suspend fun fetchRemoteSongs(songId: String, count: Int): List<SongEntity> =
		sessionManager.api.getSimilarSongs(songId, count)
			.map { it.toEntity() }

	private suspend fun cacheRemoteSongs(
		seedSongId: String,
		songs: List<SongEntity>
	): Long {
		val updatedAt = Clock.System.now().toEpochMilliseconds()
		songDao.insertSongsIgnoringConflicts(songs)
		sonicSimilarityDao.replaceSimilarSongs(
			seedSongId = seedSongId,
			similarSongs = songs.mapIndexed { index, song ->
				SonicSimilarityEntity(
					seedSongId = seedSongId,
					songId = song.songId,
					position = index,
					updatedAt = updatedAt
				)
			}
		)
		return updatedAt
	}

	private suspend fun readCache(seedSongId: String): SonicSimilarityResult? {
		val songs = sonicSimilarityDao.getSimilarSongs(seedSongId)
		if (songs.isEmpty()) return null
		return SonicSimilarityResult(
			songs = songs.map { it.toDomainModel() },
			fromCache = true,
			updatedAt = sonicSimilarityDao.getUpdatedAt(seedSongId)
		)
	}
}

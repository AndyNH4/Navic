/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.entity.DownloadStatus
import paige.navic.domain.repository.SonicSimilarityRepository

data class SonicSimilarityPrefetchState(
	val isRunning: Boolean = false,
	val processed: Int = 0,
	val total: Int = 0,
	val failed: Int = 0,
	val currentSongId: String? = null
)

class SonicSimilarityPrefetchManager(
	private val downloadDao: DownloadDao,
	private val sessionManager: SessionManager,
	private val connectivityManager: ConnectivityManager,
	private val sonicSimilarityRepository: SonicSimilarityRepository
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var prefetchJob: Job? = null

	val state: StateFlow<SonicSimilarityPrefetchState>
		field = MutableStateFlow(SonicSimilarityPrefetchState())

	val cachedSeedCount = sonicSimilarityRepository.cachedSeedCount
	val cachedRelationCount = sonicSimilarityRepository.cachedRelationCount

	suspend fun clearCache() = sonicSimilarityRepository.clearCache()

	fun startPrefetch(): Boolean {
		if (prefetchJob?.isActive == true) return false
		if (!connectivityManager.isOnline.value) return false
		if (!sessionManager.serverCapabilities.value.sonicSimilarityAvailable) return false

		prefetchJob = scope.launch { prefetchDownloadedSongs() }
		return true
	}

	fun cancelPrefetch() {
		prefetchJob?.cancel()
	}

	private suspend fun prefetchDownloadedSongs() {
		var failed = 0
		var consecutiveFailures = 0
		try {
			val downloadedIds = downloadDao.getAllDownloadsList()
				.filter { it.status == DownloadStatus.DOWNLOADED && !it.filePath.isNullOrBlank() }
				.map { it.songId }
				.distinct()
			val cachedSeedIds = sonicSimilarityRepository.getCachedSeedIds().toSet()
			val pendingIds = downloadedIds.filterNot { it in cachedSeedIds }

			state.value = state.value.copy(
				isRunning = true,
				processed = 0,
				total = pendingIds.size,
				failed = 0,
				currentSongId = null
			)

			for ((index, songId) in pendingIds.withIndex()) {
				if (!connectivityManager.isOnline.value) break
				state.value = state.value.copy(currentSongId = songId)
				try {
					sonicSimilarityRepository.refreshSimilarSongsCache(songId)
					consecutiveFailures = 0
				} catch (error: CancellationException) {
					throw error
				} catch (_: Exception) {
					failed++
					consecutiveFailures++
				}
				state.value = state.value.copy(
					processed = index + 1,
					failed = failed
				)
				if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) break
			}
		} finally {
			state.value = state.value.copy(
				isRunning = false,
				currentSongId = null
			)
		}
	}

	private companion object {
		const val MAX_CONSECUTIVE_FAILURES = 3
	}
}

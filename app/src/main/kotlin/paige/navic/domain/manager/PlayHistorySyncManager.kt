/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import paige.navic.data.database.dao.PlayEventDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.entity.PlayEventEntity
import paige.navic.data.database.entity.SongEntity
import paige.navic.data.database.mapper.toEntity
import paige.navic.data.network.NavidromeApiClient
import paige.navic.data.network.NavidromeScrobble
import paige.navic.util.Logger
import kotlin.time.Duration
import kotlin.time.Instant

class PlayHistorySyncManager(
	private val playEventDao: PlayEventDao,
	private val songDao: SongDao,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	private val navidromeApiClient: NavidromeApiClient
) {
	private val syncMutex = Mutex()

	suspend fun syncFromServer(): Result<Int> {
		if (!preferenceManager.navidromeEnhancementEnabled
			|| !preferenceManager.syncServerPlayHistory
			|| !sessionManager.serverCapabilities.value.navidromeNativeAvailable
		) {
			val error = IllegalStateException("Server play history sync is unavailable")
			Logger.w("PlayHistorySyncManager", error.message.orEmpty())
			return Result.failure(error)
		}

		return runCatching {
			syncMutex.withLock { importServerHistory() }
		}.onFailure {
			Logger.e("PlayHistorySyncManager", "Failed to sync server play history", it)
		}
	}

	private suspend fun importServerHistory(): Int {
		val fromSeconds = playEventDao.getLatestServerPlayedAt()?.epochSeconds
			?.let { (it - 1).coerceAtLeast(0) }
		val fetchedSongs = mutableMapOf<String, SongEntity?>()
		var start = 0
		var importedCount = 0

		while (true) {
			val scrobbles = navidromeApiClient.getScrobbles(
				fromSeconds = fromSeconds,
				start = start,
				end = start + PAGE_SIZE
			).getOrThrow()
			if (scrobbles.isEmpty()) break

			val localSongs = songDao.getSongsByIds(scrobbles.map { it.mediaFileId })
				.associateBy { it.songId }
			val events = scrobbles.map { scrobble ->
				val song = localSongs[scrobble.mediaFileId]
					?: fetchSong(scrobble.mediaFileId, fetchedSongs)
				scrobble.toPlayEvent(song)
			}
			importedCount += playEventDao.insertEvents(events).count { it != -1L }

			start += scrobbles.size
			if (scrobbles.size < PAGE_SIZE) break
		}

		return importedCount
	}

	private suspend fun fetchSong(
		songId: String,
		cache: MutableMap<String, SongEntity?>
	): SongEntity? {
		if (cache.containsKey(songId)) return cache[songId]

		val song = try {
			sessionManager.api.getSong(songId).toEntity()
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.w("PlayHistorySyncManager", "Failed to fetch metadata for song $songId", e)
			null
		}
		cache[songId] = song
		return song
	}

	private fun NavidromeScrobble.toPlayEvent(song: SongEntity?) = PlayEventEntity(
		songId = mediaFileId,
		songTitle = song?.title.orEmpty(),
		artistId = song?.artistId,
		artistName = song?.artistName,
		albumId = song?.belongsToAlbumId,
		albumTitle = song?.albumTitle,
		coverArtId = song?.coverArtId,
		duration = song?.duration ?: Duration.ZERO,
		playedAt = Instant.fromEpochSeconds(submissionTime),
		serverId = id
	)

	private companion object {
		const val PAGE_SIZE = 500
	}
}

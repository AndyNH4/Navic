/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.PlayEventDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.entity.PlayEventEntity
import kotlin.time.Clock
import kotlin.time.Duration

class PlayHistoryManager(
	private val playEventDao: PlayEventDao,
	private val songDao: SongDao
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	fun recordPlay(songId: String) {
		scope.launch {
			val song = songDao.getSongById(songId)
			playEventDao.insertEvent(
				PlayEventEntity(
					songId = songId,
					songTitle = song?.title ?: "",
					artistId = song?.artistId,
					artistName = song?.artistName,
					albumId = song?.belongsToAlbumId,
					albumTitle = song?.albumTitle,
					coverArtId = song?.coverArtId,
					duration = song?.duration ?: Duration.ZERO,
					playedAt = Clock.System.now()
				)
			)
		}
	}
}

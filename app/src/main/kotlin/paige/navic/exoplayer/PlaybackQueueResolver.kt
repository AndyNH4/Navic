/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.exoplayer

import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.first
import paige.navic.data.database.dao.RadioDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.domain.model.DomainSong
import paige.navic.domain.repository.PlayerStateRepository

class PlaybackQueueResolver(
	private val playerStateRepository: PlayerStateRepository,
	private val songDao: SongDao,
	private val radioDao: RadioDao
) {
	suspend fun resolve(
		items: List<MediaItem>,
		knownQueue: List<DomainSong>
	): List<DomainSong>? {
		if (items.isEmpty()) return emptyList()
		val known = buildMap {
			playerStateRepository.state.first().queue.forEach { put(it.id, it) }
			knownQueue.forEach { put(it.id, it) }
		}
		val missingIds = items.map { it.mediaId }
			.filter { id -> id !in known && !id.startsWith(RADIO_ID_PREFIX) }
			.distinct()
		val databaseSongs = if (missingIds.isEmpty()) {
			emptyMap()
		} else {
			songDao.getSongsByIds(missingIds)
				.map { it.toDomainModel() }
				.associateBy { it.id }
		}
		val radios = if (items.any { it.mediaId.startsWith(RADIO_ID_PREFIX) }) {
			radioDao.getRadios()
				.map { it.toDomainModel() }
				.associateBy { playbackRadioId(it.name) }
		} else {
			emptyMap()
		}
		return items.map { item ->
			known[item.mediaId]
				?: databaseSongs[item.mediaId]
				?: radios[item.mediaId]?.toPlaybackSong()
				?: return null
		}
	}
}

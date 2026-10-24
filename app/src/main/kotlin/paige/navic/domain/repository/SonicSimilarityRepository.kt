/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.data.database.mapper.toEntity
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.model.DomainSong

class SonicSimilarityRepository(
	private val sessionManager: SessionManager
) {
	suspend fun getSimilarSongs(songId: String, count: Int = 30): List<DomainSong> =
		withContext(Dispatchers.IO) {
			sessionManager.api.getSimilarSongs(songId, count)
				.map { it.toEntity().toDomainModel() }
		}
}

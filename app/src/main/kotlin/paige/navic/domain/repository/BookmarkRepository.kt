/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.repository

import paige.navic.data.database.mapper.toDomainModel
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.model.DomainBookmark

class BookmarkRepository(
	private val sessionManager: SessionManager
) {
	suspend fun getBookmarks(): List<DomainBookmark> =
		sessionManager.api.getBookmarks().mapNotNull { it.toDomainModel() }

	suspend fun createBookmark(songId: String, positionMs: Long) =
		sessionManager.api.createBookmark(songId, positionMs, null)

	suspend fun deleteBookmark(songId: String) =
		sessionManager.api.deleteBookmark(songId)
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import paige.navic.data.database.entity.PinnedCollectionEntity

@Dao
interface PinnedCollectionDao {
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun pinCollection(pin: PinnedCollectionEntity)

	@Query("DELETE FROM PinnedCollectionEntity WHERE collectionId = :collectionId")
	suspend fun unpinCollection(collectionId: String)

	@Query("SELECT * FROM PinnedCollectionEntity")
	suspend fun getPinnedCollections(): List<PinnedCollectionEntity>

	@Query("SELECT EXISTS(SELECT 1 FROM PinnedCollectionEntity WHERE collectionId = :collectionId)")
	suspend fun isCollectionPinned(collectionId: String): Boolean

	@Query("DELETE FROM PinnedCollectionEntity")
	suspend fun clearAllPinnedCollections()
}

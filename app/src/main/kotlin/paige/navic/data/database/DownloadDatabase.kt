/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.dao.PinnedCollectionDao
import paige.navic.data.database.entity.DownloadEntity
import paige.navic.data.database.entity.PinnedCollectionEntity

@Database(
	version = 4,
	entities = [DownloadEntity::class, PinnedCollectionEntity::class]
)
abstract class DownloadDatabase : RoomDatabase() {
	abstract fun downloadDao(): DownloadDao
	abstract fun pinnedCollectionDao(): PinnedCollectionDao
}

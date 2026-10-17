/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_22_23 = object : Migration(22, 23) {
	override suspend fun migrate(connection: SQLiteConnection) {
		connection.execSQL(
			"ALTER TABLE `ArtistEntity` ADD COLUMN `isTrackArtist` INTEGER NOT NULL DEFAULT 0"
		)
		connection.execSQL(
			"ALTER TABLE `PlayEventEntity` ADD COLUMN `serverId` INTEGER"
		)
		connection.execSQL(
			"""
			DELETE FROM `PlayEventEntity` WHERE `id` NOT IN (
				SELECT MIN(`id`) FROM `PlayEventEntity` GROUP BY `songId`, `playedAt`
			)
			""".trimIndent()
		)
		connection.execSQL(
			"CREATE UNIQUE INDEX IF NOT EXISTS `index_PlayEventEntity_songId_playedAt` ON `PlayEventEntity` (`songId`, `playedAt`)"
		)
		connection.execSQL(
			"CREATE TABLE IF NOT EXISTS `ArtistGroupEntity` (`groupKey` TEXT NOT NULL, `primaryArtistId` TEXT NOT NULL, `preferredName` TEXT, PRIMARY KEY(`groupKey`))"
		)
		connection.execSQL(
			"CREATE TABLE IF NOT EXISTS `ArtistGroupMemberEntity` (`artistId` TEXT NOT NULL, `groupKey` TEXT NOT NULL, `name` TEXT NOT NULL, `auto` INTEGER NOT NULL, PRIMARY KEY(`artistId`))"
		)
	}
}

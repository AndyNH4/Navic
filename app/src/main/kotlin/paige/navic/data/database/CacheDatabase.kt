/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.database

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.ArtistGroupDao
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.dao.GenreDao
import paige.navic.data.database.dao.LyricDao
import paige.navic.data.database.dao.PlayEventDao
import paige.navic.data.database.dao.PlaylistDao
import paige.navic.data.database.dao.RadioDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.dao.SyncActionDao
import paige.navic.data.database.entity.AlbumEntity
import paige.navic.data.database.entity.ArtistEntity
import paige.navic.data.database.entity.ArtistGroupEntity
import paige.navic.data.database.entity.ArtistGroupMemberEntity
import paige.navic.data.database.entity.DownloadEntity
import paige.navic.data.database.entity.GenreEntity
import paige.navic.data.database.entity.LyricEntity
import paige.navic.data.database.entity.PlayEventEntity
import paige.navic.data.database.entity.PlaylistEntity
import paige.navic.data.database.entity.PlaylistSongCrossRef
import paige.navic.data.database.entity.RadioEntity
import paige.navic.data.database.entity.SongEntity
import paige.navic.data.database.entity.SyncActionEntity

@Database(
	version = 23,
	entities = [
		AlbumEntity::class,
		ArtistEntity::class,
		ArtistGroupEntity::class,
		ArtistGroupMemberEntity::class,
		DownloadEntity::class,
		GenreEntity::class,
		LyricEntity::class,
		PlayEventEntity::class,
		PlaylistEntity::class,
		PlaylistSongCrossRef::class,
		RadioEntity::class,
		SongEntity::class,
		SyncActionEntity::class
	]
)
@ColumnTypeConverters(Converters::class)
abstract class CacheDatabase : RoomDatabase() {
	abstract fun albumDao(): AlbumDao
	abstract fun artistDao(): ArtistDao
	abstract fun artistGroupDao(): ArtistGroupDao
	abstract fun downloadDao(): DownloadDao
	abstract fun genreDao(): GenreDao
	abstract fun lyricDao(): LyricDao
	abstract fun playEventDao(): PlayEventDao
	abstract fun playlistDao(): PlaylistDao
	abstract fun radioDao(): RadioDao
	abstract fun songDao(): SongDao
	abstract fun syncActionDao(): SyncActionDao
}

val MIGRATION_21_22 = object : Migration(21, 22) {
	override suspend fun migrate(connection: SQLiteConnection) {
		connection.execSQL(
			"CREATE TABLE IF NOT EXISTS `PlayEventEntity` (" +
				"`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
				"`songId` TEXT NOT NULL, " +
				"`songTitle` TEXT NOT NULL, " +
				"`artistId` TEXT, " +
				"`artistName` TEXT, " +
				"`albumId` TEXT, " +
				"`albumTitle` TEXT, " +
				"`coverArtId` TEXT, " +
				"`duration` INTEGER NOT NULL, " +
				"`playedAt` INTEGER NOT NULL)"
		)
		connection.execSQL(
			"CREATE INDEX IF NOT EXISTS `index_PlayEventEntity_playedAt` ON `PlayEventEntity` (`playedAt`)"
		)
		connection.execSQL(
			"CREATE INDEX IF NOT EXISTS `index_PlayEventEntity_songId` ON `PlayEventEntity` (`songId`)"
		)
	}
}

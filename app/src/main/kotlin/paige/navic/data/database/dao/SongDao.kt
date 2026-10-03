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
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import paige.navic.data.database.entity.SongEntity
import paige.navic.util.Logger

data class GenrePlayCount(
	val genre: String,
	val playCount: Int
)

data class YearPlayCount(
	val year: Int,
	val playCount: Int
)

data class ArtistPlayCount(
	val artistId: String,
	val artistName: String?,
	val playCount: Int
)

@Dao
interface SongDao {
	@Query("SELECT * FROM SongEntity WHERE songId = :songId LIMIT 1")
	suspend fun getSongById(songId: String): SongEntity?

	@Upsert
	suspend fun insertSong(song: SongEntity)

	@Upsert
	suspend fun insertSongs(songs: List<SongEntity>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertSongsIgnoringConflicts(songs: List<SongEntity>)

	@Query("SELECT * FROM SongEntity")
	suspend fun getAllSongs(): List<SongEntity>

	@Query("SELECT SUM(playCount) FROM SongEntity")
	fun getTotalPlayCount(): Flow<Int?>

	@Query("SELECT SUM(duration * playCount) FROM SongEntity")
	fun getTotalListeningTime(): Flow<Long?>

	@Query("SELECT * FROM SongEntity WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
	fun getTopSongs(limit: Int): Flow<List<SongEntity>>

	@Query("SELECT * FROM SongEntity WHERE starredAt IS NOT NULL")
	fun getStarredSongs(): Flow<List<SongEntity>>

	@Query("SELECT belongsToAlbumId FROM SongEntity WHERE playCount > 0 GROUP BY belongsToAlbumId ORDER BY SUM(playCount) DESC LIMIT :limit")
	fun getTopAlbumIds(limit: Int): Flow<List<String>>

	@Query("SELECT artistId FROM SongEntity WHERE playCount > 0 GROUP BY artistId ORDER BY SUM(playCount) DESC LIMIT :limit")
	fun getTopArtistIds(limit: Int): Flow<List<String>>

	@Query("SELECT SUM(playCount) FROM SongEntity WHERE artistId = :artistId")
	fun getArtistPlayCount(artistId: String): Flow<Int?>

	@Query("SELECT SUM(duration * playCount) FROM SongEntity WHERE artistId = :artistId")
	fun getArtistListeningTime(artistId: String): Flow<Long?>

	@Query("SELECT SUM(duration * playCount) FROM SongEntity WHERE belongsToAlbumId = :albumId")
	fun getAlbumListeningTime(albumId: String): Flow<Long?>

	@Query("SELECT * FROM SongEntity WHERE belongsToAlbumId = :albumId")
	suspend fun getSongsByAlbumId(albumId: String): List<SongEntity>

	@Query("DELETE FROM SongEntity WHERE songId = :songId")
	suspend fun deleteSong(songId: String)

	// TODO
	@Query("SELECT EXISTS(SELECT 1 FROM SongEntity WHERE songId = :songId AND starredAt IS NOT NULL)")
	suspend fun isSongStarred(songId: String): Boolean

	@Query("SELECT userRating FROM SongEntity WHERE songId = :songId")
	suspend fun getSongRating(songId: String): Int?

	@Query("DELETE FROM SongEntity")
	suspend fun clearAllSongs()

	@Query("SELECT songId FROM SongEntity")
	suspend fun getAllSongIds(): List<String>

	@Query("SELECT * FROM SongEntity WHERE songId IN (:ids)")
	suspend fun getSongsByIds(ids: List<String>): List<SongEntity>

	@Query("SELECT * FROM SongEntity ORDER BY RANDOM() LIMIT :count")
	suspend fun getRandomSongs(count: Int): List<SongEntity>

	@Query("SELECT * FROM SongEntity WHERE starredAt IS NOT NULL ORDER BY RANDOM() LIMIT 1")
	suspend fun getRandomStarredSong(): SongEntity?

	@Query(
		"""
		SELECT * FROM SongEntity
		WHERE genre = :genre OR ('||' || genres || '||') LIKE '%||' || :genre || '||%'
		ORDER BY RANDOM() LIMIT :count
		"""
	)
	suspend fun getRandomSongsByGenre(genre: String, count: Int): List<SongEntity>

	@Query(
		"""
		SELECT * FROM SongEntity
		WHERE year IS NOT NULL AND year >= :fromYear AND year <= :toYear
		ORDER BY RANDOM() LIMIT :count
		"""
	)
	suspend fun getRandomSongsByYearRange(fromYear: Int, toYear: Int, count: Int): List<SongEntity>

	@Query(
		"""
		SELECT genre, SUM(playCount) AS playCount FROM SongEntity
		WHERE playCount > 0 AND genre IS NOT NULL AND genre != ''
		GROUP BY genre ORDER BY playCount DESC LIMIT :limit
		"""
	)
	suspend fun getTopGenresByPlayCount(limit: Int): List<GenrePlayCount>

	@Query(
		"""
		SELECT year, SUM(playCount) AS playCount FROM SongEntity
		WHERE playCount > 0 AND year IS NOT NULL AND year >= 1950
		GROUP BY year
		"""
	)
	suspend fun getPlayCountsByYear(): List<YearPlayCount>

	@Query(
		"""
		SELECT artistId, MAX(artistName) AS artistName, SUM(playCount) AS playCount FROM SongEntity
		WHERE playCount > 0 AND artistId != 'unknown artist'
		GROUP BY artistId ORDER BY playCount DESC LIMIT :limit
		"""
	)
	suspend fun getTopArtistsByPlayCount(limit: Int): List<ArtistPlayCount>

	@Query("SELECT coverArtId FROM SongEntity WHERE artistId = :artistId AND coverArtId IS NOT NULL LIMIT 1")
	suspend fun getCoverArtForArtist(artistId: String): String?

	@RawQuery
	suspend fun searchSongsFuzzy(query: RoomRawQuery): List<SongEntity>

	@Query("SELECT * FROM SongEntity WHERE artistId = :artistId")
	suspend fun getSongsByArtistId(artistId: String): List<SongEntity>

	@Transaction
	suspend fun updateSongsByAlbumId(albumId: String, remoteSongs: List<SongEntity>) {
		val remoteIds = remoteSongs.map { it.songId }.toSet()
		getSongsByAlbumId(albumId).forEach { localSong ->
			if (localSong.songId !in remoteIds) {
				Logger.w("SongDao", "song ${localSong.songId} no longer belongs to album $albumId")
				deleteSong(localSong.songId)
			}
		}
		insertSongs(remoteSongs)
	}

	@Transaction
	suspend fun updateAllSongs(remoteSongs: List<SongEntity>) {
		val remoteIds = remoteSongs.map { it.songId }.toSet()
		getAllSongIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("SongDao", "song $localId no longer exists remotely")
				deleteSong(localId)
			}
		}
		insertSongs(remoteSongs)
	}

	@Transaction
	suspend fun deleteObsoleteSongs(remoteIds: Set<String>) {
		getAllSongIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("SongDao", "song $localId no longer exists remotely")
				deleteSong(localId)
			}
		}
	}
}

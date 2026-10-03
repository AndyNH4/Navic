/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import paige.navic.data.database.entity.PlayEventEntity

data class DayPlayCount(
	val day: String,
	val playCount: Int
)

data class SongPlayAggregate(
	val songId: String,
	val songTitle: String,
	val artistName: String?,
	val coverArtId: String?,
	val playCount: Int,
	val listeningTimeMs: Long
)

data class ArtistPlayAggregate(
	val artistId: String,
	val artistName: String?,
	val coverArtId: String?,
	val playCount: Int,
	val listeningTimeMs: Long
)

data class AlbumPlayAggregate(
	val albumId: String,
	val albumTitle: String?,
	val artistName: String?,
	val coverArtId: String?,
	val playCount: Int,
	val listeningTimeMs: Long
)

@Dao
interface PlayEventDao {
	@Insert
	suspend fun insertEvent(event: PlayEventEntity)

	@Query(
		"""
		SELECT date(playedAt / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS playCount
		FROM PlayEventEntity
		WHERE playedAt >= :sinceMs
		GROUP BY day
		"""
	)
	fun getDailyPlayCounts(sinceMs: Long): Flow<List<DayPlayCount>>

	@Query(
		"""
		SELECT DISTINCT date(playedAt / 1000, 'unixepoch', 'localtime') AS day
		FROM PlayEventEntity
		ORDER BY day ASC
		"""
	)
	fun getActiveDays(): Flow<List<String>>

	@Query(
		"""
		SELECT songId, songTitle, artistName, MAX(coverArtId) AS coverArtId,
			COUNT(*) AS playCount, SUM(duration) AS listeningTimeMs
		FROM PlayEventEntity
		WHERE playedAt >= :sinceMs
		GROUP BY songId
		ORDER BY playCount DESC, listeningTimeMs DESC
		LIMIT :limit
		"""
	)
	fun getTopSongs(sinceMs: Long, limit: Int): Flow<List<SongPlayAggregate>>

	@Query(
		"""
		SELECT artistId, MAX(artistName) AS artistName, MAX(coverArtId) AS coverArtId,
			COUNT(*) AS playCount, SUM(duration) AS listeningTimeMs
		FROM PlayEventEntity
		WHERE playedAt >= :sinceMs AND artistId IS NOT NULL
		GROUP BY artistId
		ORDER BY playCount DESC, listeningTimeMs DESC
		LIMIT :limit
		"""
	)
	fun getTopArtists(sinceMs: Long, limit: Int): Flow<List<ArtistPlayAggregate>>

	@Query(
		"""
		SELECT albumId, MAX(albumTitle) AS albumTitle, MAX(artistName) AS artistName,
			MAX(coverArtId) AS coverArtId, COUNT(*) AS playCount, SUM(duration) AS listeningTimeMs
		FROM PlayEventEntity
		WHERE playedAt >= :sinceMs AND albumId IS NOT NULL
		GROUP BY albumId
		ORDER BY playCount DESC, listeningTimeMs DESC
		LIMIT :limit
		"""
	)
	fun getTopAlbums(sinceMs: Long, limit: Int): Flow<List<AlbumPlayAggregate>>

	@Query("SELECT SUM(duration) FROM PlayEventEntity WHERE playedAt >= :sinceMs")
	fun getTotalListeningTimeMs(sinceMs: Long): Flow<Long?>

	@Query(
		"""
		SELECT s.genre
		FROM PlayEventEntity e JOIN SongEntity s ON s.songId = e.songId
		WHERE CAST(strftime('%H', e.playedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) IN (:hours)
			AND s.genre IS NOT NULL AND s.genre != ''
		GROUP BY s.genre
		ORDER BY COUNT(*) DESC
		LIMIT 1
		"""
	)
	suspend fun getTopGenreForHours(hours: List<Int>): String?

	@Query("DELETE FROM PlayEventEntity")
	suspend fun clearAllEvents()
}

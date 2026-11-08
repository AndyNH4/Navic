/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import paige.navic.data.database.entity.SongEntity
import paige.navic.data.database.entity.SonicSimilarityEntity

@Dao
interface SonicSimilarityDao {
	@Query(
		"""
		SELECT SongEntity.* FROM SonicSimilarityEntity
		INNER JOIN SongEntity ON SongEntity.songId = SonicSimilarityEntity.songId
		WHERE SonicSimilarityEntity.seedSongId = :seedSongId
		ORDER BY SonicSimilarityEntity.position ASC
		"""
	)
	suspend fun getSimilarSongs(seedSongId: String): List<SongEntity>

	@Query("SELECT MAX(updatedAt) FROM SonicSimilarityEntity WHERE seedSongId = :seedSongId")
	suspend fun getUpdatedAt(seedSongId: String): Long?

	@Query("SELECT seedSongId FROM SonicSimilarityEntity GROUP BY seedSongId")
	suspend fun getCachedSeedIds(): List<String>

	@Query("SELECT COUNT(DISTINCT seedSongId) FROM SonicSimilarityEntity")
	fun getCachedSeedCount(): Flow<Int>

	@Query("SELECT COUNT(*) FROM SonicSimilarityEntity")
	fun getCachedRelationCount(): Flow<Int>

	@Upsert
	suspend fun insertSimilarSongs(similarSongs: List<SonicSimilarityEntity>)

	@Query("DELETE FROM SonicSimilarityEntity WHERE seedSongId = :seedSongId")
	suspend fun deleteSimilarSongs(seedSongId: String)

	@Transaction
	suspend fun replaceSimilarSongs(
		seedSongId: String,
		similarSongs: List<SonicSimilarityEntity>
	) {
		deleteSimilarSongs(seedSongId)
		insertSimilarSongs(similarSongs)
	}

	@Query("DELETE FROM SonicSimilarityEntity")
	suspend fun clearAll()
}

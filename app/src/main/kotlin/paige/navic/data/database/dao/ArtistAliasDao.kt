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
import paige.navic.data.database.entity.ArtistAliasEntity

@Dao
interface ArtistAliasDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAlias(alias: ArtistAliasEntity): Long

	@Query("SELECT * FROM ArtistAliasEntity WHERE aliasId = :aliasId LIMIT 1")
	suspend fun getAliasById(aliasId: Long): ArtistAliasEntity?

	@Query("SELECT * FROM ArtistAliasEntity WHERE groupKey = :groupKey ORDER BY languageTag COLLATE NOCASE, name COLLATE NOCASE, aliasId")
	suspend fun getAliasesByGroupKey(groupKey: String): List<ArtistAliasEntity>

	@RawQuery
	suspend fun searchAliasesFuzzy(query: RoomRawQuery): List<ArtistAliasEntity>

	@Query("DELETE FROM ArtistAliasEntity WHERE aliasId = :aliasId")
	suspend fun deleteAlias(aliasId: Long)

	@Query("DELETE FROM ArtistAliasEntity WHERE groupKey = :groupKey")
	suspend fun deleteAliasesByGroupKey(groupKey: String)

	@Query("DELETE FROM ArtistAliasEntity WHERE groupKey NOT IN (SELECT groupKey FROM ArtistGroupEntity)")
	suspend fun deleteOrphanAliases()
}

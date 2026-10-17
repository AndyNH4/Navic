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
import paige.navic.data.database.entity.ArtistGroupEntity
import paige.navic.data.database.entity.ArtistGroupMemberEntity

@Dao
interface ArtistGroupDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertGroup(group: ArtistGroupEntity)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertMember(member: ArtistGroupMemberEntity)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertMembers(members: List<ArtistGroupMemberEntity>)

	@Query("DELETE FROM ArtistGroupEntity WHERE groupKey = :groupKey")
	suspend fun deleteGroup(groupKey: String)

	@Query("DELETE FROM ArtistGroupMemberEntity WHERE artistId = :artistId")
	suspend fun deleteMember(artistId: String)

	@Query("DELETE FROM ArtistGroupMemberEntity WHERE groupKey = :groupKey")
	suspend fun deleteMembersByGroupKey(groupKey: String)

	@Query("SELECT * FROM ArtistGroupEntity WHERE groupKey = :groupKey LIMIT 1")
	suspend fun getGroupByKey(groupKey: String): ArtistGroupEntity?

	@Query("SELECT * FROM ArtistGroupEntity")
	suspend fun getAllGroups(): List<ArtistGroupEntity>

	@Query("SELECT * FROM ArtistGroupMemberEntity")
	suspend fun getAllMembers(): List<ArtistGroupMemberEntity>

	@Query("SELECT * FROM ArtistGroupMemberEntity WHERE artistId = :artistId LIMIT 1")
	suspend fun getMemberByArtistId(artistId: String): ArtistGroupMemberEntity?

	@Query("SELECT * FROM ArtistGroupMemberEntity WHERE groupKey = :groupKey")
	suspend fun getMembersByGroupKey(groupKey: String): List<ArtistGroupMemberEntity>

	@RawQuery
	suspend fun searchMembersFuzzy(query: RoomRawQuery): List<ArtistGroupMemberEntity>

	@Query("DELETE FROM ArtistGroupMemberEntity WHERE auto = 1")
	suspend fun clearAutoMembers()
}

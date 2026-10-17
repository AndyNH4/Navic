/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.ArtistGroupDao
import paige.navic.data.database.entity.ArtistEntity
import paige.navic.data.database.entity.ArtistGroupEntity
import paige.navic.data.database.entity.ArtistGroupMemberEntity
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.domain.model.DomainArtist
import java.util.UUID

data class ArtistGroupInfo(
	val group: ArtistGroupEntity,
	val members: List<ArtistGroupMemberEntity>
)

class ArtistGroupManager(
	private val artistDao: ArtistDao,
	private val artistGroupDao: ArtistGroupDao
) {
	suspend fun rebuildAutoGroups() {
		val artists = artistDao.getAllArtistsList()
		val artistsById = artists.associateBy { it.artistId }
		val existingMembers = artistGroupDao.getAllMembers()
		existingMembers.filter { it.artistId !in artistsById }.forEach {
			artistGroupDao.deleteMember(it.artistId)
		}

		val manualMembers = existingMembers.filter { !it.auto && it.artistId in artistsById }
		val manuallyGroupedIds = manualMembers.map { it.artistId }.toSet()
		artistGroupDao.clearAutoMembers()

		val automaticMembers = mutableListOf<ArtistGroupMemberEntity>()
		val automaticGroupKeys = mutableSetOf<String>()
		artists.filter { !it.musicBrainzId.isNullOrBlank() }
			.groupBy { it.musicBrainzId!!.trim() }
			.forEach { (musicBrainzId, matchingArtists) ->
				val distinctArtists = matchingArtists.distinctBy { it.artistId }
				if (distinctArtists.size < 2) return@forEach
				val eligibleArtists = distinctArtists.filter { it.artistId !in manuallyGroupedIds }
				if (eligibleArtists.size < 2) return@forEach

				val groupKey = "mbid:$musicBrainzId"
				automaticGroupKeys += groupKey
				automaticMembers += eligibleArtists.map { artist ->
					ArtistGroupMemberEntity(
						artistId = artist.artistId,
						groupKey = groupKey,
						name = artist.name,
						auto = true
					)
				}
			}

		automaticGroupKeys.forEach { groupKey ->
			val members = automaticMembers.filter { it.groupKey == groupKey }
			val existingGroup = artistGroupDao.getGroupByKey(groupKey)
			val memberIds = members.map { it.artistId }.toSet()
			val primaryArtistId = existingGroup?.primaryArtistId
				?.takeIf { it in memberIds }
				?: selectPrimaryArtist(members.mapNotNull { artistsById[it.artistId] }).artistId
			artistGroupDao.upsertGroup(
				ArtistGroupEntity(
					groupKey = groupKey,
					primaryArtistId = primaryArtistId,
					preferredName = existingGroup?.preferredName
				)
			)
		}
		artistGroupDao.upsertMembers(automaticMembers)

		val currentMembers = manualMembers + automaticMembers
		val membersByGroup = currentMembers.groupBy { it.groupKey }
		artistGroupDao.getAllGroups().forEach { group ->
			val members = membersByGroup[group.groupKey].orEmpty()
			if (members.isEmpty()) {
				artistGroupDao.deleteGroup(group.groupKey)
			} else if (group.primaryArtistId !in members.map { it.artistId }) {
				val primary = selectPrimaryArtist(members.mapNotNull { artistsById[it.artistId] })
				artistGroupDao.upsertGroup(group.copy(primaryArtistId = primary.artistId))
			}
		}
	}

	suspend fun mergeArtists(primaryArtistId: String, otherArtistId: String): String {
		require(primaryArtistId != otherArtistId)
		val primaryArtist = requireNotNull(artistDao.getArtistById(primaryArtistId))
		val otherArtist = requireNotNull(artistDao.getArtistById(otherArtistId))
		val primaryMembership = artistGroupDao.getMemberByArtistId(primaryArtistId)
		val otherMembership = artistGroupDao.getMemberByArtistId(otherArtistId)
		val sourceGroupKeys = setOfNotNull(
			primaryMembership?.groupKey,
			otherMembership?.groupKey
		)
		val sourceGroups = artistGroupDao.getAllGroups().filter { it.groupKey in sourceGroupKeys }
		val sourceMembers = artistGroupDao.getAllMembers().filter { it.groupKey in sourceGroupKeys }
		val targetGroupKey = when {
			primaryMembership?.auto == false -> primaryMembership.groupKey
			otherMembership?.auto == false -> otherMembership.groupKey
			else -> "manual:${UUID.randomUUID()}"
		}
		val preferredName = sourceGroups.firstOrNull { it.groupKey == targetGroupKey }?.preferredName
			?: sourceGroups.firstOrNull { it.groupKey == primaryMembership?.groupKey }?.preferredName
			?: sourceGroups.firstOrNull { it.groupKey == otherMembership?.groupKey }?.preferredName

		artistGroupDao.upsertGroup(
			ArtistGroupEntity(
				groupKey = targetGroupKey,
				primaryArtistId = primaryArtistId,
				preferredName = preferredName
			)
		)

		val sourceMembersById = sourceMembers.associateBy { it.artistId }
		val memberIds = buildSet {
			add(primaryArtistId)
			add(otherArtistId)
			addAll(sourceMembersById.keys)
		}
		memberIds.forEach { artistId ->
			val artist = when (artistId) {
				primaryArtistId -> primaryArtist
				otherArtistId -> otherArtist
				else -> artistDao.getArtistById(artistId)
			}
			if (artist == null) {
				artistGroupDao.deleteMember(artistId)
			} else {
				artistGroupDao.upsertMember(
					ArtistGroupMemberEntity(
						artistId = artistId,
						groupKey = targetGroupKey,
						name = artist.name,
						auto = false
					)
				)
			}
		}
		sourceGroupKeys.filter { it != targetGroupKey }.forEach {
			artistGroupDao.deleteGroup(it)
		}
		return targetGroupKey
	}

	suspend fun removeFromGroup(artistId: String) {
		val membership = artistGroupDao.getMemberByArtistId(artistId) ?: return
		val group = artistGroupDao.getGroupByKey(membership.groupKey) ?: return
		val remainingMembers = artistGroupDao.getMembersByGroupKey(group.groupKey)
			.filter { it.artistId != artistId }
		artistGroupDao.deleteMember(artistId)

		if (!membership.auto) {
			if (remainingMembers.size <= 1) {
				remainingMembers.forEach { artistGroupDao.deleteMember(it.artistId) }
				artistGroupDao.deleteGroup(group.groupKey)
			} else if (group.primaryArtistId == artistId) {
				val artists = remainingMembers.mapNotNull { artistDao.getArtistById(it.artistId) }
				artistGroupDao.upsertGroup(
					group.copy(primaryArtistId = selectPrimaryArtist(artists).artistId)
				)
			}
			return
		}

		if (remainingMembers.isEmpty()) {
			artistGroupDao.deleteGroup(group.groupKey)
			return
		}
		val targetGroupKey = "manual:${UUID.randomUUID()}"
		val primaryArtistId = group.primaryArtistId.takeIf { id ->
			remainingMembers.any { it.artistId == id }
		} ?: selectPrimaryArtist(
			remainingMembers.mapNotNull { artistDao.getArtistById(it.artistId) }
		).artistId
		artistGroupDao.upsertGroup(
			group.copy(groupKey = targetGroupKey, primaryArtistId = primaryArtistId)
		)
		artistGroupDao.upsertMembers(
			remainingMembers.map { it.copy(groupKey = targetGroupKey, auto = false) }
		)
		artistGroupDao.deleteGroup(group.groupKey)
	}

	suspend fun disbandGroup(groupKey: String) {
		artistGroupDao.deleteMembersByGroupKey(groupKey)
		artistGroupDao.deleteGroup(groupKey)
	}

	suspend fun setPreferredName(groupKey: String, name: String?) {
		val group = requireNotNull(artistGroupDao.getGroupByKey(groupKey))
		artistGroupDao.upsertGroup(group.copy(preferredName = name?.trim()?.takeIf { it.isNotEmpty() }))
	}

	suspend fun setPrimaryArtist(groupKey: String, artistId: String) {
		val group = requireNotNull(artistGroupDao.getGroupByKey(groupKey))
		val membership = requireNotNull(artistGroupDao.getMemberByArtistId(artistId))
		require(membership.groupKey == groupKey)
		artistGroupDao.upsertGroup(group.copy(primaryArtistId = artistId))
	}

	suspend fun getGroupInfo(artistId: String): ArtistGroupInfo? {
		val membership = artistGroupDao.getMemberByArtistId(artistId) ?: return null
		val group = artistGroupDao.getGroupByKey(membership.groupKey) ?: return null
		val members = artistGroupDao.getMembersByGroupKey(group.groupKey)
		if (members.isEmpty()) return null
		return ArtistGroupInfo(group, members)
	}

	suspend fun collapseArtists(artists: List<DomainArtist>): List<DomainArtist> {
		if (artists.isEmpty()) return emptyList()
		val groups = artistGroupDao.getAllGroups().associateBy { it.groupKey }
		val memberships = artistGroupDao.getAllMembers().associateBy { it.artistId }
		val collapsed = mutableListOf<DomainArtist>()
		val emittedGroups = mutableSetOf<String>()

		artists.forEach { artist ->
			val membership = memberships[artist.id]
			if (membership == null) {
				collapsed += artist
				return@forEach
			}
			if (!emittedGroups.add(membership.groupKey)) return@forEach
			val group = groups[membership.groupKey]
			if (group == null) {
				collapsed += artist
				return@forEach
			}
			val members = artistGroupDao.getMembersByGroupKey(group.groupKey)
			val groupArtists = artistDao.getArtistsByIds(members.map { it.artistId })
				.map { it.toDomainModel() }
			val primaryArtist = groupArtists.firstOrNull { it.id == group.primaryArtistId }
				?: artistDao.getArtistById(group.primaryArtistId)?.toDomainModel()
				?: artist
			val currentMembers = members.map { member ->
				groupArtists.firstOrNull { it.id == member.artistId }
					?.let { member.copy(name = it.name) }
					?: member
			}
			collapsed += primaryArtist.copy(
				name = ArtistNameResolver.resolveDisplayName(primaryArtist, currentMembers, group),
				albumCount = groupArtists.sumOf { it.albumCount },
				starredAt = groupArtists.mapNotNull { it.starredAt }.maxOrNull(),
				isTrackArtist = groupArtists.isNotEmpty() && groupArtists.all { it.isTrackArtist }
			)
		}
		return collapsed
	}

	private fun selectPrimaryArtist(artists: List<ArtistEntity>): ArtistEntity =
		artists.minWithOrNull(
			compareByDescending<ArtistEntity> { it.albumCount }
				.thenBy { it.name.length }
				.thenBy { it.name }
				.thenBy { it.artistId }
		) ?: throw IllegalStateException("Cannot select a primary artist from an empty group")
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import paige.navic.data.database.dao.ArtistAliasDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.ArtistGroupDao
import paige.navic.data.database.entity.ArtistAliasEntity
import paige.navic.data.database.entity.ArtistEntity
import paige.navic.data.database.entity.ArtistGroupEntity
import paige.navic.data.database.entity.ArtistGroupMemberEntity
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.domain.model.DomainArtist
import java.util.Locale
import java.util.UUID

data class ArtistGroupInfo(
	val group: ArtistGroupEntity,
	val members: List<ArtistGroupMemberEntity>,
	val aliases: List<ArtistAliasEntity> = emptyList()
)

class ArtistGroupManager(
	private val artistDao: ArtistDao,
	private val artistGroupDao: ArtistGroupDao,
	private val artistAliasDao: ArtistAliasDao
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
				artistAliasDao.deleteAliasesByGroupKey(group.groupKey)
				artistGroupDao.deleteGroup(group.groupKey)
			} else if (group.primaryArtistId !in members.map { it.artistId }) {
				val primary = selectPrimaryArtist(members.mapNotNull { artistsById[it.artistId] })
				artistGroupDao.upsertGroup(group.copy(primaryArtistId = primary.artistId))
			}
		}
		artistAliasDao.deleteOrphanAliases()
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
		sourceGroupKeys.filter { it != targetGroupKey }.forEach { sourceGroupKey ->
			artistAliasDao.getAliasesByGroupKey(sourceGroupKey).forEach { alias ->
				artistAliasDao.upsertAlias(alias.copy(aliasId = 0, groupKey = targetGroupKey))
			}
			artistAliasDao.deleteAliasesByGroupKey(sourceGroupKey)
			artistGroupDao.deleteGroup(sourceGroupKey)
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
				val remainingMember = remainingMembers.singleOrNull()
				remainingMembers.forEach { artistGroupDao.deleteMember(it.artistId) }
				if (remainingMember != null) {
					preserveGroupForMember(group, remainingMember)
				}
				artistAliasDao.deleteAliasesByGroupKey(group.groupKey)
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
			artistAliasDao.deleteAliasesByGroupKey(group.groupKey)
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
		artistAliasDao.getAliasesByGroupKey(group.groupKey).forEach { alias ->
			artistAliasDao.upsertAlias(alias.copy(aliasId = 0, groupKey = targetGroupKey))
		}
		artistAliasDao.deleteAliasesByGroupKey(group.groupKey)
		artistGroupDao.deleteGroup(group.groupKey)
	}

	suspend fun disbandGroup(groupKey: String) {
		val group = artistGroupDao.getGroupByKey(groupKey) ?: return
		val members = artistGroupDao.getMembersByGroupKey(groupKey)
		val primaryMember = members.firstOrNull { it.artistId == group.primaryArtistId }
			?: members.firstOrNull()
		artistGroupDao.deleteMembersByGroupKey(groupKey)
		if (primaryMember != null) {
			preserveGroupForMember(group, primaryMember)
		}
		artistAliasDao.deleteAliasesByGroupKey(groupKey)
		artistGroupDao.deleteGroup(groupKey)
	}

	suspend fun addAlias(artistId: String, name: String, languageTag: String) {
		val normalizedName = name.trim().also { require(it.isNotEmpty()) }
		val normalizedTag = normalizeLanguageTag(languageTag)
		val groupKey = ensureGroup(artistId)
		artistAliasDao.upsertAlias(
			ArtistAliasEntity(
				groupKey = groupKey,
				name = normalizedName,
				languageTag = normalizedTag
			)
		)
	}

	suspend fun updateAlias(aliasId: Long, name: String, languageTag: String) {
		val alias = requireNotNull(artistAliasDao.getAliasById(aliasId))
		val normalizedName = name.trim().also { require(it.isNotEmpty()) }
		artistAliasDao.upsertAlias(
			alias.copy(
				name = normalizedName,
				languageTag = normalizeLanguageTag(languageTag)
			)
		)
	}

	suspend fun deleteAlias(aliasId: Long) {
		artistAliasDao.deleteAlias(aliasId)
	}

	suspend fun setPreferredNameForArtist(artistId: String, name: String?) {
		if (name.isNullOrBlank() && artistGroupDao.getMemberByArtistId(artistId) == null) return
		setPreferredName(ensureGroup(artistId), name)
	}

	suspend fun setPreferredName(groupKey: String, name: String?) {
		val group = requireNotNull(artistGroupDao.getGroupByKey(groupKey))
		artistGroupDao.upsertGroup(group.copy(preferredName = name?.trim()?.takeIf { it.isNotEmpty() }))
	}

	private suspend fun ensureGroup(artistId: String): String {
		artistGroupDao.getMemberByArtistId(artistId)?.let { return it.groupKey }
		val artist = requireNotNull(artistDao.getArtistById(artistId))
		val groupKey = "manual:${UUID.randomUUID()}"
		artistGroupDao.upsertGroup(
			ArtistGroupEntity(groupKey = groupKey, primaryArtistId = artistId)
		)
		artistGroupDao.upsertMember(
			ArtistGroupMemberEntity(
				artistId = artistId,
				groupKey = groupKey,
				name = artist.name,
				auto = false
			)
		)
		return groupKey
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
		return ArtistGroupInfo(
			group = group,
			members = members,
			aliases = artistAliasDao.getAliasesByGroupKey(group.groupKey)
		)
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
			val aliases = artistAliasDao.getAliasesByGroupKey(group.groupKey)
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
				name = ArtistNameResolver.resolveDisplayName(primaryArtist, currentMembers, group, aliases),
				albumCount = groupArtists.sumOf { it.albumCount },
				starredAt = groupArtists.mapNotNull { it.starredAt }.maxOrNull(),
				isTrackArtist = groupArtists.isNotEmpty() && groupArtists.all { it.isTrackArtist }
			)
		}
		return collapsed
	}

	private suspend fun preserveGroupForMember(
		group: ArtistGroupEntity,
		member: ArtistGroupMemberEntity
	) {
		val aliases = artistAliasDao.getAliasesByGroupKey(group.groupKey)
		if (aliases.isEmpty() && group.preferredName == null) return
		val targetGroupKey = "manual:${UUID.randomUUID()}"
		artistGroupDao.upsertGroup(
			group.copy(
				groupKey = targetGroupKey,
				primaryArtistId = member.artistId
			)
		)
		artistGroupDao.upsertMember(
			member.copy(groupKey = targetGroupKey, auto = false)
		)
		aliases.forEach { alias ->
			artistAliasDao.upsertAlias(alias.copy(aliasId = 0, groupKey = targetGroupKey))
		}
	}

	private fun normalizeLanguageTag(languageTag: String): String {
		val tag = languageTag.trim()
		if (tag.isEmpty()) return tag
		val locale = Locale.forLanguageTag(tag)
		require(locale.language.isNotBlank()) { "Invalid language tag" }
		val normalized = locale.toLanguageTag()
		require(normalized != "und" || tag.startsWith("und-", ignoreCase = true)) {
			"Invalid language tag"
		}
		return normalized
	}

	private fun selectPrimaryArtist(artists: List<ArtistEntity>): ArtistEntity =
		artists.minWithOrNull(
			compareByDescending<ArtistEntity> { it.albumCount }
				.thenBy { it.name.length }
				.thenBy { it.name }
				.thenBy { it.artistId }
		) ?: throw IllegalStateException("Cannot select a primary artist from an empty group")
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import paige.navic.data.database.entity.ArtistGroupEntity
import paige.navic.data.database.entity.ArtistGroupMemberEntity
import paige.navic.domain.model.DomainArtist
import java.util.Locale

object ArtistNameResolver {
	fun resolve(
		candidateNames: List<String>,
		primaryName: String,
		preferredName: String? = null,
		locale: Locale = Locale.getDefault()
	): String {
		preferredName?.takeIf { it.isNotBlank() }?.let { return it }
		val names = candidateNames.filter { it.isNotBlank() }.distinct()
		if (names.isEmpty()) return primaryName

		val localizedName = when (locale.language.lowercase()) {
			"zh" -> names.firstOrNull { it.hasCjkIdeograph() }
			"ja" -> names.firstOrNull { it.hasKana() }
				?: names.firstOrNull { it.hasCjkIdeograph() }
			else -> names.firstOrNull { it.isPrimarilyLatin() }
		}
		return localizedName ?: primaryName
	}

	fun resolveDisplayName(
		artist: DomainArtist,
		members: List<ArtistGroupMemberEntity>,
		group: ArtistGroupEntity? = null,
		locale: Locale = Locale.getDefault()
	): String = resolve(
		candidateNames = members.map { it.name },
		primaryName = artist.name,
		preferredName = group?.preferredName,
		locale = locale
	)

	private fun String.hasCjkIdeograph() = any { it in '\u4E00'..'\u9FFF' }

	private fun String.hasKana() = any { it in '\u3040'..'\u30FF' }

	private fun String.isPrimarilyLatin(): Boolean {
		val latinLetters = count { it in 'A'..'Z' || it in 'a'..'z' }
		if (latinLetters == 0) return false
		val otherLetters = count { it.isLetter() && (it !in 'A'..'Z' && it !in 'a'..'z') }
		return latinLetters >= otherLetters
	}
}

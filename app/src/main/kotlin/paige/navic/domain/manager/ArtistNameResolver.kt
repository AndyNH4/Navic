/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import paige.navic.data.database.entity.ArtistAliasEntity
import paige.navic.data.database.entity.ArtistGroupEntity
import paige.navic.data.database.entity.ArtistGroupMemberEntity
import paige.navic.domain.model.DomainArtist
import java.util.Locale

data class ArtistNameCandidate(
	val name: String,
	val languageTag: String = "",
	val custom: Boolean = false
)

object ArtistNameResolver {
	fun resolve(
		candidateNames: List<String>,
		primaryName: String,
		preferredName: String? = null,
		locale: Locale = Locale.getDefault()
	): String = resolveCandidates(
		candidateNames = candidateNames.map { ArtistNameCandidate(it) },
		primaryName = primaryName,
		preferredName = preferredName,
		locale = locale
	)

	fun resolveDisplayName(
		artist: DomainArtist,
		members: List<ArtistGroupMemberEntity>,
		group: ArtistGroupEntity? = null,
		aliases: List<ArtistAliasEntity> = emptyList(),
		locale: Locale = Locale.getDefault()
	): String = resolveCandidates(
		candidateNames = members.map { ArtistNameCandidate(it.name) } + aliases.map {
			ArtistNameCandidate(
				name = it.name,
				languageTag = it.languageTag,
				custom = true
			)
		},
		primaryName = artist.name,
		preferredName = group?.preferredName,
		locale = locale
	)

	private fun resolveCandidates(
		candidateNames: List<ArtistNameCandidate>,
		primaryName: String,
		preferredName: String?,
		locale: Locale
	): String {
		preferredName?.takeIf { it.isNotBlank() }?.let { return it }
		val candidates = (listOf(ArtistNameCandidate(primaryName)) + candidateNames)
			.filter { it.name.isNotBlank() }
		val bestMatch = candidates.mapIndexedNotNull { index, candidate ->
			val score = matchScore(candidate, locale)
			if (score > 0) ScoredCandidate(candidate.name, score, index) else null
		}.maxWithOrNull(
			compareByDescending<ScoredCandidate> { it.score }.thenBy { it.index }
		)
		return bestMatch?.name ?: primaryName
	}

	private fun matchScore(candidate: ArtistNameCandidate, locale: Locale): Int {
		val languageTag = candidate.languageTag.trim()
		val taggedLocale = languageTag.takeIf { it.isNotEmpty() }?.let(Locale::forLanguageTag)
		val language = taggedLocale?.language
			?.takeIf { it.isNotBlank() && it != "und" }
			?: candidate.name.inferredLanguage()
		val script = taggedLocale?.script
			?.takeIf { it.isNotBlank() }
			?: candidate.name.inferredScript()
		val targetScript = locale.script.ifBlank { locale.defaultScript() }
		var score = 0
		if (language.equals(locale.language, ignoreCase = true)) score += 300
		if (!targetScript.isNullOrBlank() && script.equals(targetScript, ignoreCase = true)) {
			score += 200
		}
		if (languageTag.equals(locale.toLanguageTag(), ignoreCase = true)) score += 100
		if (candidate.custom && languageTag.isNotEmpty() && score > 0) score += 25
		return score
	}

	private fun Locale.defaultScript(): String? = when (language.lowercase()) {
		"zh" -> if (country.uppercase() in setOf("TW", "HK", "MO")) "Hant" else "Hans"
		"ja" -> "Jpan"
		"ko" -> "Kore"
		"ru", "uk", "bg" -> "Cyrl"
		"ar" -> "Arab"
		else -> "Latn"
	}

	private fun String.inferredLanguage(): String? = when {
		hasKana() -> "ja"
		hasHangul() -> "ko"
		hasCjkIdeograph() -> "zh"
		else -> null
	}

	private fun String.inferredScript(): String? = when {
		hasKana() -> "Jpan"
		hasHangul() -> "Kore"
		hasCjkIdeograph() -> "Han"
		isPrimarilyLatin() -> "Latn"
		else -> null
	}

	private fun String.hasCjkIdeograph() = any { it in '\u4E00'..'\u9FFF' }

	private fun String.hasKana() = any { it in '\u3040'..'\u30FF' }

	private fun String.hasHangul() = any { it in '\uAC00'..'\uD7AF' }

	private fun String.isPrimarilyLatin(): Boolean {
		val latinLetters = count { it in 'A'..'Z' || it in 'a'..'z' }
		if (latinLetters == 0) return false
		val otherLetters = count { it.isLetter() && (it !in 'A'..'Z' && it !in 'a'..'z') }
		return latinLetters >= otherLetters
	}

	private data class ScoredCandidate(
		val name: String,
		val score: Int,
		val index: Int
	)
}

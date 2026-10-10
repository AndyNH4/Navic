/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model.lyrics

import androidx.compose.runtime.Immutable

@Immutable
data class ParsedLyrics(
	val lines: List<LyricsLine>,
	val translationLanguage: String? = null
)

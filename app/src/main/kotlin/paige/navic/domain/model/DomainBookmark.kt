/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

@Immutable
data class DomainBookmark(
	val songId: String,
	val positionMs: Long,
	val comment: String?,
	val createdAt: Instant,
	val modifiedAt: Instant,
	val song: DomainSong
)

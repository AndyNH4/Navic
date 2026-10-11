/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model

import androidx.compose.runtime.Immutable
import kotlin.time.Duration

@Immutable
data class DomainStarredCollection(
	override val id: String,
	override val name: String?,
	override val coverArtId: String?,
	override val duration: Duration?,
	override val songCount: Int,
	override val songs: List<DomainSong>
) : DomainSongCollection {
	companion object {
		const val STARRED_COLLECTION_ID = "starred-songs"
	}
}

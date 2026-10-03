/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class DomainFolder(
	val id: String,
	val name: String,
	val isMusicFolder: Boolean = false
)

@Immutable
data class DomainFolderContent(
	val folders: ImmutableList<DomainFolder>,
	val songs: ImmutableList<DomainSong>
)

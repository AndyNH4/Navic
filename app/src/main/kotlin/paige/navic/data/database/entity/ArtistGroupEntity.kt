/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity
data class ArtistGroupEntity(
	@PrimaryKey val groupKey: String,
	val primaryArtistId: String,
	val preferredName: String? = null
)

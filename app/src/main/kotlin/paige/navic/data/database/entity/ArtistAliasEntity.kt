/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
	indices = [
		Index(value = ["groupKey"]),
		Index(value = ["name"]),
		Index(value = ["groupKey", "name", "languageTag"], unique = true)
	]
)
data class ArtistAliasEntity(
	@PrimaryKey(autoGenerate = true) val aliasId: Long = 0,
	val groupKey: String,
	val name: String,
	val languageTag: String = ""
)

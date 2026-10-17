/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Duration
import kotlin.time.Instant

@Entity(
	indices = [
		Index(value = ["playedAt"]),
		Index(value = ["songId"]),
		Index(value = ["songId", "playedAt"], unique = true)
	]
)
data class PlayEventEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val songId: String,
	val songTitle: String,
	val artistId: String?,
	val artistName: String?,
	val albumId: String?,
	val albumTitle: String?,
	val coverArtId: String?,
	val duration: Duration,
	val playedAt: Instant,
	val serverId: Long? = null
)

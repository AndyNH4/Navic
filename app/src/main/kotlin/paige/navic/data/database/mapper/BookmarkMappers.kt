/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.database.mapper

import paige.navic.domain.model.DomainBookmark
import dev.zt64.subsonic.api.model.Bookmark as ApiBookmark
import dev.zt64.subsonic.api.model.Song as ApiSong
import dev.zt64.subsonic.api.model.SubsonicResource as ApiSubsonicResource

fun ApiBookmark<ApiSubsonicResource>.toDomainModel(): DomainBookmark? {
	val song = (entry as? ApiSong)?.toEntity()?.toDomainModel() ?: return null
	return DomainBookmark(
		songId = song.id,
		positionMs = position.toLong(),
		comment = comment,
		createdAt = createdAt,
		modifiedAt = modifiedAt,
		song = song
	)
}

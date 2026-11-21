/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util

import androidx.room3.RoomRawQuery
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainAlbumListType
import paige.navic.domain.model.DomainArtistSongScope
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.DomainSongListType

// TODO: sort with sql instead
fun ImmutableList<DomainSong>.sortedByListType(
	listType: DomainSongListType,
	albums: List<DomainAlbum>
): ImmutableList<DomainSong> {
	return when (listType) {
		DomainSongListType.FrequentlyPlayed -> sortedByDescending { it.playCount }
		DomainSongListType.Newest -> sortedByDescending {
			albums
				.firstOrNull { album -> album.id == it.albumId }
				?.createdAt
		}

		DomainSongListType.Random -> shuffled()

		DomainSongListType.Rating -> sortedByDescending { it.userRating ?: 0 }
		DomainSongListType.Year -> sortedByDescending { it.year }
		DomainSongListType.Alphabetical -> sortedBy { it.title.lowercase() }
		DomainSongListType.RecentlyStarred -> sortedByDescending { it.starredAt }

		is DomainSongListType.ByArtist -> run {
			val artistIds = listType.artistIds + listType.artistId
			val ownAlbumIds = albums.filter { album ->
				album.artistId in artistIds
					|| (album.artistName != null && album.artistName in listType.artistNames)
			}.mapTo(mutableSetOf()) { it.id }
			filter { song ->
				val isOwnSong = song.albumId in ownAlbumIds
				val isParticipation = song.artistId in artistIds
					|| song.artists.any { artist -> artist.id in artistIds }
				when (listType.scope) {
					DomainArtistSongScope.All -> isOwnSong || isParticipation
					DomainArtistSongScope.Albums -> isOwnSong
					DomainArtistSongScope.Participations -> isParticipation && !isOwnSong
				}
			}.sortedByDescending { it.playCount }
		}

		is DomainSongListType.ByGenre -> filter {
			it.genre == listType.genre
				|| listType.genre in it.genres
		}.sortedByDescending { it.playCount }
	}.toImmutableList()
}

fun DomainAlbumListType.toSqlQuery(): RoomRawQuery {
	var where: String? = null
	var orderBy: String
	val args = mutableListOf<Any>()

	when (this) {
		DomainAlbumListType.AlphabeticalByArtist -> orderBy = "LOWER(artistName) ASC"
		DomainAlbumListType.AlphabeticalByName -> orderBy = "LOWER(name) ASC"
		DomainAlbumListType.Frequent -> {
			where = "playCount != 0"
			orderBy = "playCount DESC"
		}

		DomainAlbumListType.Highest -> orderBy = "userRating DESC"
		DomainAlbumListType.Newest -> orderBy = "createdAt DESC"
		DomainAlbumListType.Random -> orderBy = "RANDOM()"
		DomainAlbumListType.Recent -> orderBy = "lastPlayedAt DESC"

		DomainAlbumListType.Year -> {
			orderBy = "year DESC"
		}

		is DomainAlbumListType.ByGenre -> {
			where = "genre = ?"
			orderBy = "LOWER(name) ASC"
			args.add(genre)
		}

		is DomainAlbumListType.ByYear -> {
			where = "COALESCE(year, 0) BETWEEN ? AND ?"
			orderBy = "LOWER(name) ASC"
			args.add(fromYear)
			args.add(toYear)
		}
	}

	val whereClause = where?.let { " WHERE $it" } ?: ""
	val sql = "SELECT * FROM AlbumEntity$whereClause ORDER BY $orderBy"

	return RoomRawQuery(sql) { statement ->
		args.forEachIndexed { index, arg ->
			val bindIndex = index + 1
			when (arg) {
				is String -> statement.bindText(bindIndex, arg)
				is Int -> statement.bindInt(bindIndex, arg)
				is Long -> statement.bindLong(bindIndex, arg)
				is Float -> statement.bindFloat(bindIndex, arg)
				is Double -> statement.bindDouble(bindIndex, arg)
				is Boolean -> statement.bindInt(bindIndex, if (arg) 1 else 0)
			}
		}
	}
}

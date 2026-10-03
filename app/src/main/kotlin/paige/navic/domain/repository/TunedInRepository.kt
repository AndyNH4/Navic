/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.repository

import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import paige.navic.data.database.dao.GenreDao
import paige.navic.data.database.dao.PlayEventDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.entity.SongEntity
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.data.database.mapper.toEntity
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.model.DomainMix
import paige.navic.domain.model.DomainMixKind
import paige.navic.domain.model.DomainMixStrategy
import paige.navic.domain.model.DomainSong
import paige.navic.util.Logger

class TunedInRepository(
	private val songDao: SongDao,
	private val playEventDao: PlayEventDao,
	private val genreDao: GenreDao,
	private val sessionManager: SessionManager
) {
	companion object {
		const val LIST_LENGTH = 20
		const val HEAVY_ROTATION_MIN_SONGS = 5
	}

	suspend fun generateMixes(isOnline: Boolean): List<DomainMix> = withContext(Dispatchers.IO) {
		val mixes = mutableListOf<DomainMix>()

		val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
		val hour = now.hour
		val windowHours = listOf((hour + 23) % 24, hour, (hour + 1) % 24)

		// 1. "Right Now" — genre the user listens to around the current hour
		val hourGenre = playEventDao.getTopGenreForHours(windowHours)
			?.takeIf { genre -> songDao.getRandomSongsByGenre(genre, 1).isNotEmpty() }
		val topGenres = songDao.getTopGenresByPlayCount(limit = 5).map { it.genre }
			.ifEmpty { genreDao.getGenres().take(5).map { it.genreName } }

		if (hourGenre != null) {
			mixes += DomainMix(
				id = "right-now",
				kind = DomainMixKind.RightNow,
				subtitleArg = hourGenre,
				coverArtIds = coverArtForGenre(hourGenre),
				strategy = DomainMixStrategy.RandomByGenre(hourGenre, LIST_LENGTH)
			)
		} else {
			mixes += DomainMix(
				id = "right-now",
				kind = DomainMixKind.RightNow,
				strategy = DomainMixStrategy.Random(LIST_LENGTH)
			)
		}

		// 2. Genre mix for the top genre that isn't already covered by "Right Now"
		val genreMix = topGenres.firstOrNull { it != hourGenre }
		if (genreMix != null) {
			mixes += DomainMix(
				id = "genre-$genreMix",
				kind = DomainMixKind.Genre,
				titleArg = genreMix,
				coverArtIds = coverArtForGenre(genreMix),
				strategy = DomainMixStrategy.RandomByGenre(genreMix, LIST_LENGTH)
			)
		}

		// 3. "Time Machine" — decade weighted by play count, sqrt-softened so the
		// top decade doesn't dominate; a null entry at the average weight
		// occasionally yields a cross-decade random mix instead
		val decade = pickDecade()
		if (decade != null) {
			mixes += DomainMix(
				id = "time-machine",
				kind = DomainMixKind.TimeMachine,
				titleArg = "${decade}s",
				coverArtIds = songDao.getRandomSongsByYearRange(decade, decade + 9, 2)
					.mapNotNull { it.coverArtId },
				strategy = DomainMixStrategy.RandomByYearRange(decade, decade + 9, LIST_LENGTH)
			)
		} else {
			mixes += DomainMix(
				id = "time-machine",
				kind = DomainMixKind.TimeMachine,
				strategy = DomainMixStrategy.Random(LIST_LENGTH)
			)
		}

		// 4. "Mix It Up" — completely random
		mixes += DomainMix(
			id = "mix-it-up",
			kind = DomainMixKind.MixItUp,
			strategy = DomainMixStrategy.Random(LIST_LENGTH)
		)

		// 5. "Deep Cuts" — similar artists (online only)
		if (isOnline) {
			val artists = songDao.getTopArtistsByPlayCount(limit = 5)
			val picked = weightedPick(
				artists.map { it to it.playCount.toDouble() },
				nullWeight = artists.map { it.playCount }.average().takeIf { it > 0 }
			)
			if (picked != null) {
				mixes += DomainMix(
					id = "deep-cuts",
					kind = DomainMixKind.DeepCuts,
					subtitleArg = picked.artistName,
					coverArtIds = listOfNotNull(songDao.getCoverArtForArtist(picked.artistId)),
					strategy = DomainMixStrategy.SimilarToArtist(picked.artistId, LIST_LENGTH)
				)
			} else {
				mixes += DomainMix(
					id = "deep-cuts",
					kind = DomainMixKind.DeepCuts,
					strategy = DomainMixStrategy.Random(LIST_LENGTH)
				)
			}
		}

		// 6. "Favorites Radio" — seeded by a random starred song (online only)
		if (isOnline) {
			val seed = songDao.getRandomStarredSong()
			if (seed != null) {
				mixes += DomainMix(
					id = "favorites-radio",
					kind = DomainMixKind.FavoritesRadio,
					subtitleArg = seed.title,
					coverArtIds = listOfNotNull(seed.coverArtId),
					strategy = DomainMixStrategy.SimilarToSong(seed.songId, LIST_LENGTH)
				)
			}
		}

		// 7. "Genre Blend" — top two genres, half each
		if (topGenres.size >= 2) {
			val (first, second) = topGenres
			mixes += DomainMix(
				id = "genre-blend",
				kind = DomainMixKind.GenreBlend,
				titleArg = first,
				subtitleArg = second,
				coverArtIds = (coverArtForGenre(first, 1) + coverArtForGenre(second, 1)).distinct(),
				strategy = DomainMixStrategy.GenreBlend(
					listOf(
						first to (LIST_LENGTH + 1) / 2,
						second to (LIST_LENGTH + 1) / 2
					)
				)
			)
		}

		// 8. "Heavy Rotation" — most played songs in the last 7 days
		val sevenDaysAgoMs = (Clock.System.now() - 7.days).toEpochMilliseconds()
		val heavyRotation = playEventDao.getTopSongs(sevenDaysAgoMs, LIST_LENGTH).first()
		if (heavyRotation.size >= HEAVY_ROTATION_MIN_SONGS) {
			mixes += DomainMix(
				id = "heavy-rotation",
				kind = DomainMixKind.HeavyRotation,
				coverArtIds = heavyRotation.mapNotNull { it.coverArtId }.distinct().take(2),
				strategy = DomainMixStrategy.RecentTopSongs(heavyRotation.map { it.songId })
			)
		}

		mixes
	}

	suspend fun fetchMixSongs(mix: DomainMix): List<DomainSong> = withContext(Dispatchers.IO) {
		try {
			when (val strategy = mix.strategy) {
				is DomainMixStrategy.RandomByGenre ->
					resolveGenreSlot(strategy.genre, strategy.size).map { it.toDomainModel() }

				is DomainMixStrategy.RandomByYearRange ->
					songDao.getRandomSongsByYearRange(strategy.fromYear, strategy.toYear, strategy.size)
						.map { it.toDomainModel() }

				is DomainMixStrategy.SimilarToArtist -> {
					val songs = sessionManager.api.getSimilarSongsID3(strategy.artistId, strategy.count)
						.map { it.toEntity().toDomainModel() }
						.shuffled()
					songs.ifEmpty { randomSongs() }
				}

				is DomainMixStrategy.SimilarToSong -> {
					val songs = sessionManager.api.getSimilarSongs(strategy.songId, strategy.count)
						.map { it.toEntity().toDomainModel() }
						.shuffled()
					songs.ifEmpty { randomSongs() }
				}

				is DomainMixStrategy.GenreBlend ->
					strategy.genres
						.flatMap { (genre, size) -> resolveGenreSlot(genre, size) }
						.distinctBy { it.songId }
						.shuffled()
						.take(LIST_LENGTH)
						.map { it.toDomainModel() }

				is DomainMixStrategy.Random -> randomSongs(strategy.size)

				is DomainMixStrategy.RecentTopSongs -> {
					val order = strategy.songIds.withIndex().associate { it.value to it.index }
					songDao.getSongsByIds(strategy.songIds)
						.sortedBy { order[it.songId] }
						.map { it.toDomainModel() }
				}
			}
		} catch (e: Exception) {
			Logger.e("TunedInRepository", "Failed to fetch songs for mix ${mix.id}, falling back to random", e)
			try {
				randomSongs()
			} catch (e: Exception) {
				Logger.e("TunedInRepository", "Failed to fetch random fallback songs", e)
				emptyList()
			}
		}
	}

	/**
	 * Random pull for one genre; if a compound tag (e.g. "Folk, World, & Country")
	 * matches nothing, retry each split part and merge, mirroring servers that
	 * store split single-value genres.
	 */
	private suspend fun resolveGenreSlot(genre: String, size: Int): List<SongEntity> {
		val primary = songDao.getRandomSongsByGenre(genre, size)
		if (primary.isNotEmpty()) return primary
		val parts = genre.split(',', '/', '&', ';').map { it.trim() }.filter { it.isNotEmpty() }
		if (parts.size <= 1) return primary
		return parts.flatMap { songDao.getRandomSongsByGenre(it, size) }
	}

	private suspend fun coverArtForGenre(genre: String, count: Int = 2): List<String> =
		songDao.getRandomSongsByGenre(genre, count).mapNotNull { it.coverArtId }

	private suspend fun randomSongs(count: Int = LIST_LENGTH): List<DomainSong> =
		songDao.getRandomSongs(count).map { it.toDomainModel() }

	private suspend fun pickDecade(): Int? {
		val decadeCounts = mutableMapOf<Int, Int>()
		for (entry in songDao.getPlayCountsByYear()) {
			val decade = entry.year / 10 * 10
			decadeCounts[decade] = (decadeCounts[decade] ?: 0) + entry.playCount
		}
		if (decadeCounts.isEmpty()) return null

		val softened = decadeCounts.map { (decade, count) -> decade to sqrt(count.toDouble()) }
		return weightedPick(softened, nullWeight = softened.map { it.second }.average())
	}

	private fun <T> weightedPick(
		candidates: List<Pair<T, Double>>,
		nullWeight: Double? = null
	): T? {
		if (candidates.isEmpty()) return null
		val pool = mutableListOf<Pair<T?, Double>>()
		candidates.forEach { pool.add(it.first to it.second) }
		nullWeight?.let { pool.add(null to it) }
		val total = pool.sumOf { it.second }
		var roll = Random.nextDouble() * total
		for ((value, weight) in pool) {
			roll -= weight
			if (roll <= 0) return value
		}
		return pool.last().first
	}
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.stats.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import paige.navic.R
import paige.navic.data.database.dao.AlbumPlayAggregate
import paige.navic.data.database.dao.PlayEventDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.dao.SongPlayAggregate
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.model.DomainSong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class ArtistStats(
	val artist: DomainArtist,
	val playCount: Int,
	val listeningTime: Duration
)

data class StatisticsState(
	val totalPlays: Int = 0,
	val totalDuration: Duration = Duration.ZERO,
	val isLoading: Boolean = true
)

data class LocalStatsState(
	val dayCounts: Map<LocalDate, Int> = emptyMap(),
	val playsLastYear: Int = 0,
	val currentStreak: Int = 0,
	val longestStreak: Int = 0
)

enum class TopRange(val displayName: Int) {
	Weeks4(R.string.stats_range_4_weeks),
	Months6(R.string.stats_range_6_months),
	AllTime(R.string.stats_range_all_time);

	fun sinceMs(): Long {
		val timeZone = TimeZone.currentSystemDefault()
		val today = Clock.System.todayIn(timeZone)
		val since = when (this) {
			Weeks4 -> today.minus(28, DateTimeUnit.DAY)
			Months6 -> today.minus(6, DateTimeUnit.MONTH)
			AllTime -> LocalDate(1970, 1, 1)
		}
		return since.atStartOfDayIn(timeZone).toEpochMilliseconds()
	}
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
	private val songDao: SongDao,
	private val playEventDao: PlayEventDao
) : ViewModel() {

	val topRange: StateFlow<TopRange>
		field = MutableStateFlow(TopRange.Weeks4)

	val starredSongs: StateFlow<List<DomainSong>> = songDao
		.getStarredSongs()
		.map { list -> list.map { song -> song.toDomainModel() } }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val state: StateFlow<StatisticsState> = combine(
		songDao.getTotalPlayCount().map { it ?: 0 },
		songDao.getTotalListeningTime().map { (it ?: 0L).milliseconds }
	) { total, duration ->
		StatisticsState(
			totalPlays = total,
			totalDuration = duration,
			isLoading = false
		)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsState())

	val localStats: StateFlow<LocalStatsState> = combine(
		playEventDao.getDailyPlayCounts(heatmapSinceMs()),
		playEventDao.getActiveDays()
	) { daily, activeDays ->
		val dayCounts = daily.associate { LocalDate.parse(it.day) to it.playCount }
		val (currentStreak, longestStreak) = computeStreaks(activeDays)
		LocalStatsState(
			dayCounts = dayCounts,
			playsLastYear = dayCounts.values.sum(),
			currentStreak = currentStreak,
			longestStreak = longestStreak
		)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalStatsState())

	val topSongs: StateFlow<List<SongPlayAggregate>> = topRange
		.flatMapLatest { playEventDao.getTopSongs(it.sinceMs(), limit = 10) }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val topAlbums: StateFlow<List<AlbumPlayAggregate>> = topRange
		.flatMapLatest { playEventDao.getTopAlbums(it.sinceMs(), limit = 10) }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val topArtists: StateFlow<List<ArtistStats>> = topRange
		.flatMapLatest { playEventDao.getTopArtists(it.sinceMs(), limit = 10) }
		.map { aggregates ->
			aggregates.map { aggregate ->
				ArtistStats(
					artist = DomainArtist(
						id = aggregate.artistId,
						name = aggregate.artistName ?: "",
						coverArtId = aggregate.coverArtId
					),
					playCount = aggregate.playCount,
					listeningTime = aggregate.listeningTimeMs.milliseconds
				)
			}
		}
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val localListeningTime: StateFlow<Duration> = topRange
		.flatMapLatest { playEventDao.getTotalListeningTimeMs(it.sinceMs()) }
		.map { (it ?: 0L).milliseconds }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Duration.ZERO)

	fun setTopRange(range: TopRange) {
		topRange.value = range
	}

	fun clearLocalStats() {
		viewModelScope.launch {
			playEventDao.clearAllEvents()
		}
	}

	private fun heatmapSinceMs(): Long {
		val timeZone = TimeZone.currentSystemDefault()
		val today = Clock.System.todayIn(timeZone)
		return today.minus(1, DateTimeUnit.YEAR).atStartOfDayIn(timeZone).toEpochMilliseconds()
	}

	private fun computeStreaks(activeDays: List<String>): Pair<Int, Int> {
		val dates = activeDays
			.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
			.sorted()
		if (dates.isEmpty()) return 0 to 0

		var longest = 1
		var run = 1
		for (i in 1 until dates.size) {
			run = if (dates[i - 1].plus(1, DateTimeUnit.DAY) == dates[i]) run + 1 else 1
			if (run > longest) longest = run
		}

		val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
		val dateSet = dates.toSet()
		var current = 0
		var cursor = if (today in dateSet) today else today.minus(1, DateTimeUnit.DAY)
		while (cursor in dateSet) {
			current++
			cursor = cursor.minus(1, DateTimeUnit.DAY)
		}
		return current to longest
	}
}

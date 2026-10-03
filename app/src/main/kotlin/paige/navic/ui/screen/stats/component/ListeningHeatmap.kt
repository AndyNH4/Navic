/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.stats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import paige.navic.R
import kotlin.math.ceil
import kotlin.time.Clock

private const val WEEK_COUNT = 53

@Composable
fun ListeningHeatmap(
	dayCounts: Map<LocalDate, Int>,
	modifier: Modifier = Modifier
) {
	val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
	val weeks = remember(today) {
		val currentWeekMonday = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
		val firstMonday = currentWeekMonday.minus(WEEK_COUNT - 1, DateTimeUnit.WEEK)
		(0 until WEEK_COUNT).map { week ->
			(0 until 7).map { day ->
				firstMonday.plus(week * 7 + day, DateTimeUnit.DAY)
			}
		}
	}
	val maxCount = remember(dayCounts) { dayCounts.values.maxOrNull() ?: 0 }

	val activeColor = MaterialTheme.colorScheme.primary
	val levelColors = listOf(
		MaterialTheme.colorScheme.surfaceContainerHighest,
		activeColor.copy(alpha = 0.25f),
		activeColor.copy(alpha = 0.5f),
		activeColor.copy(alpha = 0.75f),
		activeColor
	)

	val scrollState = rememberScrollState()
	LaunchedEffect(Unit) {
		snapshotFlow { scrollState.maxValue }.collect { max ->
			if (max > 0) scrollState.scrollTo(max)
		}
	}

	Column(modifier = modifier) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.horizontalScroll(scrollState),
			horizontalArrangement = Arrangement.spacedBy(3.dp)
		) {
			weeks.forEach { week ->
				Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
					week.forEach { day ->
						val count = dayCounts[day] ?: 0
						val level = when {
							day > today -> -1
							count <= 0 -> 0
							maxCount <= 4 -> count
							else -> ceil(count * 4f / maxCount).toInt().coerceIn(1, 4)
						}
						Box(
							modifier = Modifier
								.size(12.dp)
								.clip(RoundedCornerShape(3.dp))
								.background(
									if (level < 0) Color.Transparent else levelColors[level]
								)
						)
					}
				}
			}
		}
		Row(
			modifier = Modifier.padding(top = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp)
		) {
			val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
			Text(
				text = stringResource(R.string.legend_less),
				style = MaterialTheme.typography.labelSmall,
				color = labelColor
			)
			levelColors.forEach { color ->
				Box(
					modifier = Modifier
						.size(12.dp)
						.clip(RoundedCornerShape(3.dp))
						.background(color)
				)
			}
			Text(
				text = stringResource(R.string.legend_more),
				style = MaterialTheme.typography.labelSmall,
				color = labelColor
			)
		}
	}
}

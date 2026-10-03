/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.tunedin.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.materialkolor.rememberDynamicColorScheme
import dev.zt64.compose.pipette.HsvColor
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.DomainMix
import paige.navic.domain.model.DomainMixKind
import paige.navic.domain.model.settings.ThemeMode
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Artist
import paige.navic.icons.outlined.Bedtime
import paige.navic.icons.outlined.Genre
import paige.navic.icons.outlined.History
import paige.navic.icons.outlined.LibraryMusic
import paige.navic.icons.outlined.Repeat
import paige.navic.icons.outlined.Shuffle
import paige.navic.icons.outlined.Star
import paige.navic.ui.component.common.CoverArt
import paige.navic.ui.theme.defaultFont

fun LazyGridScope.tunedInSection(
	mixes: List<DomainMix>,
	loadingMixId: String?,
	onPlayMix: (DomainMix) -> Unit
) {
	if (mixes.isEmpty()) return

	item(span = { GridItemSpan(maxLineSpan) }) {
		Text(
			text = stringResource(R.string.tunedin_section_title),
			style = MaterialTheme.typography.titleMediumEmphasized,
			fontWeight = FontWeight(600),
			modifier = Modifier
				.heightIn(min = 32.dp)
				.padding(top = 12.dp, start = 16.dp)
				.semantics { heading() }
		)
	}

	item(span = { GridItemSpan(maxLineSpan) }) {
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = PaddingValues(horizontal = 16.dp)
		) {
			items(mixes, key = { it.id }) { mix ->
				TunedInCard(
					// fixed height: wrapped text must not resize sibling cards mid-scroll
					modifier = Modifier.animateItem().width(230.dp).height(118.dp),
					mix = mix,
					loading = loadingMixId == mix.id,
					onClick = { onPlayMix(mix) }
				)
			}
		}
	}
}

@Composable
fun TunedInCard(
	modifier: Modifier = Modifier,
	mix: DomainMix,
	loading: Boolean,
	onClick: () -> Unit
) {
	val inDarkTheme = isSystemInDarkTheme()
	val preferenceManager = koinInject<PreferenceManager>()
	val isDark = remember(preferenceManager.themeMode) {
		when (preferenceManager.themeMode) {
			ThemeMode.System -> inDarkTheme
			ThemeMode.Dark -> true
			ThemeMode.Light -> false
		}
	}
	val seedColor = remember(mix.id) { mixSeedColor(mix) }
	val colorScheme = rememberDynamicColorScheme(seedColor = seedColor, isDark = isDark)

	Surface(
		modifier = modifier,
		color = colorScheme.primary,
		contentColor = colorScheme.onPrimary,
		shape = MaterialTheme.shapes.medium,
		shadowElevation = 2.dp,
		onClick = dropUnlessResumed { onClick() }
	) {
		Box {
			Box(Modifier.align(Alignment.CenterEnd)) {
				mix.coverArtIds.getOrNull(0)?.let { coverArtId ->
					CoverArt(
						coverArtId = coverArtId,
						modifier = Modifier
							.padding(top = 10.dp)
							.rotate(10f)
							.size(90.dp)
							.offset(x = 5.dp, y = 5.dp),
						shape = MaterialTheme.shapes.medium,
						shadowElevation = 3.dp
					)
				}
				mix.coverArtIds.getOrNull(1)?.let { coverArtId ->
					CoverArt(
						coverArtId = coverArtId,
						modifier = Modifier
							.padding(top = 13.dp)
							.rotate(25f)
							.size(90.dp)
							.offset(x = 25.dp, y = 15.dp),
						shape = MaterialTheme.shapes.medium,
						shadowElevation = 10.dp
					)
				}
			}
			Column(
				modifier = Modifier.fillMaxWidth().padding(
					start = 8.dp,
					end = 85.dp,
					top = 10.dp,
					bottom = 10.dp
				).align(Alignment.TopStart)
			) {
				Icon(
					imageVector = mixIcon(mix),
					contentDescription = null,
					modifier = Modifier.size(20.dp).padding(bottom = 2.dp)
				)
				Text(
					mixTitle(mix),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight(600),
					fontFamily = defaultFont(round = 100f),
					overflow = TextOverflow.Ellipsis,
					maxLines = 1
				)
				Text(
					mixSubtitle(mix),
					style = MaterialTheme.typography.titleSmall,
					overflow = TextOverflow.Ellipsis,
					maxLines = 2
				)
			}
			if (loading) {
				CircularProgressIndicator(
					modifier = Modifier.align(Alignment.Center).size(32.dp),
					color = colorScheme.onPrimary
				)
			}
		}
	}
}

private val timeSlotHourRanges = listOf(
	5..7 to R.string.tunedin_time_early_morning,
	8..10 to R.string.tunedin_time_morning,
	11..13 to R.string.tunedin_time_midday,
	14..16 to R.string.tunedin_time_afternoon,
	17..19 to R.string.tunedin_time_evening,
	20..22 to R.string.tunedin_time_night
)

private fun currentHour(): Int =
	Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour

@Composable
private fun timeSlotLabel(): String {
	val hour = currentHour()
	val label = timeSlotHourRanges
		.firstOrNull { hour in it.first }?.second
		?: R.string.tunedin_time_late_night
	return stringResource(label)
}

@Composable
private fun mixTitle(mix: DomainMix): String = when (mix.kind) {
	DomainMixKind.RightNow -> timeSlotLabel().replaceFirstChar { it.uppercase() }
	DomainMixKind.Genre -> stringResource(R.string.tunedin_mix_genre, mix.titleArg.orEmpty())
	DomainMixKind.TimeMachine -> mix.titleArg
		?.let { stringResource(R.string.tunedin_mix_decade, it) }
		?: stringResource(R.string.tunedin_mix_time_machine)
	DomainMixKind.MixItUp -> stringResource(R.string.tunedin_mix_mix_it_up)
	DomainMixKind.DeepCuts -> if (mix.subtitleArg != null) {
		stringResource(R.string.tunedin_mix_deep_cuts)
	} else {
		stringResource(R.string.tunedin_mix_surprise_me)
	}
	DomainMixKind.FavoritesRadio -> stringResource(R.string.tunedin_mix_favorites_radio)
	DomainMixKind.GenreBlend -> stringResource(
		R.string.tunedin_mix_genre_blend,
		mix.titleArg.orEmpty(),
		mix.subtitleArg.orEmpty()
	)
	DomainMixKind.HeavyRotation -> stringResource(R.string.tunedin_mix_heavy_rotation)
}

@Composable
private fun mixSubtitle(mix: DomainMix): String = when (mix.kind) {
	DomainMixKind.RightNow -> mix.subtitleArg
		?.let { stringResource(R.string.tunedin_subtitle_right_now_genre, it, timeSlotLabel()) }
		?: stringResource(R.string.tunedin_subtitle_right_now_random, timeSlotLabel())
	DomainMixKind.Genre -> stringResource(R.string.tunedin_subtitle_genre)
	DomainMixKind.TimeMachine -> if (mix.titleArg != null) {
		stringResource(R.string.tunedin_subtitle_decade)
	} else {
		stringResource(R.string.tunedin_subtitle_time_machine)
	}
	DomainMixKind.MixItUp -> stringResource(R.string.tunedin_subtitle_mix_it_up)
	DomainMixKind.DeepCuts -> mix.subtitleArg
		?.let { stringResource(R.string.tunedin_subtitle_deep_cuts, it) }
		?: stringResource(R.string.tunedin_subtitle_surprise_me)
	DomainMixKind.FavoritesRadio -> stringResource(
		R.string.tunedin_subtitle_favorites_radio,
		mix.subtitleArg.orEmpty()
	)
	DomainMixKind.GenreBlend -> stringResource(R.string.tunedin_subtitle_genre_blend)
	DomainMixKind.HeavyRotation -> stringResource(R.string.tunedin_subtitle_heavy_rotation)
}

private fun mixIcon(mix: DomainMix): ImageVector = when (mix.kind) {
	DomainMixKind.RightNow ->
		if (currentHour() in 5..19) Icons.Outlined.LibraryMusic else Icons.Outlined.Bedtime
	DomainMixKind.Genre -> Icons.Outlined.Genre
	DomainMixKind.TimeMachine -> Icons.Outlined.History
	DomainMixKind.MixItUp -> Icons.Outlined.Shuffle
	DomainMixKind.DeepCuts -> Icons.Outlined.Artist
	DomainMixKind.FavoritesRadio -> Icons.Outlined.Star
	DomainMixKind.GenreBlend -> Icons.Outlined.Genre
	DomainMixKind.HeavyRotation -> Icons.Outlined.Repeat
}

private fun mixSeedColor(mix: DomainMix): Color = when (mix.kind) {
	DomainMixKind.RightNow -> when (currentHour()) {
		in 5..7 -> Color(0xFFF59E0B)
		in 8..10 -> Color(0xFFF97316)
		in 11..13 -> Color(0xFF3B82F6)
		in 14..16 -> Color(0xFF2563EB)
		in 17..19 -> Color(0xFFF97316)
		in 20..22 -> Color(0xFF6366F1)
		else -> Color(0xFF312E81)
	}
	DomainMixKind.Genre -> HsvColor(
		hue = abs((mix.titleArg ?: mix.id).hashCode() % 360).toFloat(),
		saturation = 0.6f,
		value = 0.5f
	).toColor()
	DomainMixKind.TimeMachine -> Color(0xFFD97706)
	DomainMixKind.MixItUp -> Color(0xFF3B82F6)
	DomainMixKind.DeepCuts -> Color(0xFF7C3AED)
	DomainMixKind.FavoritesRadio -> Color(0xFFE11D48)
	DomainMixKind.GenreBlend -> Color(0xFF059669)
	DomainMixKind.HeavyRotation -> Color(0xFF6366F1)
}

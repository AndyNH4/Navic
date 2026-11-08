/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.di.LocalSizeClass
import paige.navic.domain.manager.PreferenceManager
import paige.navic.ui.component.common.SegmentedListItemDefaults
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.component.layout.NestedTopBarDefaults
import paige.navic.ui.screen.settings.component.SettingsGroup
import paige.navic.ui.screen.settings.component.SettingsGroupDefaults
import paige.navic.ui.screen.settings.component.SettingsNavItem
import paige.navic.ui.screen.settings.component.SettingsToggleItem
import paige.navic.ui.screen.settings.dialog.LyricsPrioritySheet

@Composable
fun SettingsLyricsScreen() {
	val sizeClass = LocalSizeClass.current
	val hideBack = sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val preferenceManager = koinInject<PreferenceManager>()
	var lyricProvidersSheetOpen by rememberSaveable { mutableStateOf(false) }

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(R.string.title_lyrics)) },
				navigationAction = {
					if (!hideBack) {
						NestedTopBarDefaults.NavigationAction()
					}
				}
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				SettingsGroup(title = { Text(stringResource(R.string.title_lyrics_sources_content)) }) {
					SettingsNavItem(
						onClick = { lyricProvidersSheetOpen = true },
						content = { Text(stringResource(R.string.action_configure_lyric_providers)) },
						supportingContent = { Text(stringResource(R.string.subtitle_configure_lyric_providers)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsShowTranslation,
						onCheckedChange = { preferenceManager.lyricsShowTranslation = it },
						content = { Text(stringResource(R.string.option_lyrics_show_translation)) },
						supportingContent = { Text(stringResource(R.string.subtitle_lyrics_show_translation)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2)
					)
				}

				SettingsGroup(title = { Text(stringResource(R.string.title_lyrics_dynamic_effects)) }) {
					SettingsToggleItem(
						checked = preferenceManager.lyricsAutoscroll,
						onCheckedChange = { preferenceManager.lyricsAutoscroll = it },
						content = { Text(stringResource(R.string.option_lyrics_autoscroll)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 6)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsBeatByBeat,
						onCheckedChange = { preferenceManager.lyricsBeatByBeat = it },
						content = { Text(stringResource(R.string.option_lyrics_beat_by_beat)) },
						supportingContent = { Text(stringResource(R.string.subtitle_lyrics_beat_by_beat)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 6)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsFakeBeatByBeat,
						onCheckedChange = { preferenceManager.lyricsFakeBeatByBeat = it },
						enabled = preferenceManager.lyricsBeatByBeat,
						content = { Text(stringResource(R.string.option_lyrics_fake_beat_by_beat)) },
						supportingContent = { Text(stringResource(R.string.subtitle_lyrics_fake_beat_by_beat)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 6)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsWordScale,
						onCheckedChange = { preferenceManager.lyricsWordScale = it },
						enabled = preferenceManager.lyricsBeatByBeat,
						content = { Text(stringResource(R.string.option_lyrics_word_scale)) },
						supportingContent = { Text(stringResource(R.string.subtitle_lyrics_word_scale)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 6)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsBlur,
						onCheckedChange = { preferenceManager.lyricsBlur = it },
						content = { Text(stringResource(R.string.option_lyrics_blur)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 4, count = 6)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsBrightInactive,
						onCheckedChange = { preferenceManager.lyricsBrightInactive = it },
						content = { Text(stringResource(R.string.option_lyrics_bright_inactive)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 5, count = 6)
					)
				}

				SettingsGroup(title = { Text(stringResource(R.string.title_behaviour)) }) {
					SettingsToggleItem(
						checked = preferenceManager.lyricsKeepAlive,
						onCheckedChange = { preferenceManager.lyricsKeepAlive = it },
						content = { Text(stringResource(R.string.option_lyrics_keep_alive)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
					)
				}
			}
		}
	}

	LyricsPrioritySheet(
		presented = lyricProvidersSheetOpen,
		onDismissRequest = { lyricProvidersSheetOpen = false }
	)
}

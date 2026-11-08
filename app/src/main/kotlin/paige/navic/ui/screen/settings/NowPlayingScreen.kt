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
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.di.LocalSizeClass
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.settings.CoverArtTapAction
import paige.navic.domain.model.settings.NowPlayingBackgroundStyle
import paige.navic.domain.model.settings.ToolbarPosition
import paige.navic.ui.component.common.SegmentedListItem
import paige.navic.ui.component.common.SegmentedListItemDefaults
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.component.layout.NestedTopBarDefaults
import paige.navic.ui.screen.settings.component.SettingsChoiceItem
import paige.navic.ui.screen.settings.component.SettingsGroup
import paige.navic.ui.screen.settings.component.SettingsGroupDefaults
import paige.navic.ui.screen.settings.component.SettingsToggleItem
import paige.navic.ui.screen.settings.dialog.NowPlayingSliderStyleDialog

@Composable
fun SettingsNowPlayingScreen() {
	val sizeClass = LocalSizeClass.current
	val hideBack = sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val preferenceManager = koinInject<PreferenceManager>()
	var sliderStyleDialogOpen by rememberSaveable { mutableStateOf(false) }

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(R.string.title_now_playing)) },
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
				SettingsGroup {
					SettingsChoiceItem(
						content = { Text(stringResource(R.string.option_cover_art_action)) },
						choices = CoverArtTapAction.entries.toImmutableList(),
						selectedChoice = preferenceManager.nowPlayingCoverArtAction,
						onChoiceSelected = { preferenceManager.nowPlayingCoverArtAction = it },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 3)
					)
					SettingsChoiceItem(
						content = { Text(stringResource(R.string.option_now_playing_background_style)) },
						description = stringResource(R.string.subtitle_now_playing_background_style),
						choices = NowPlayingBackgroundStyle.entries.toImmutableList(),
						selectedChoice = preferenceManager.nowPlayingBackgroundStyle,
						onChoiceSelected = { preferenceManager.nowPlayingBackgroundStyle = it },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 3)
					)
					SegmentedListItem(
						onClick = { sliderStyleDialogOpen = true },
						content = { Text(stringResource(R.string.option_now_playing_slider_style)) },
						supportingContent = { Text(stringResource(preferenceManager.nowPlayingSliderStyle.displayName)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 3)
					)
				}

				SettingsGroup(title = { Text(stringResource(R.string.title_layout)) }) {
					SettingsToggleItem(
						checked = preferenceManager.nowPlayingSongInfo,
						onCheckedChange = { preferenceManager.nowPlayingSongInfo = it },
						content = { Text(stringResource(R.string.option_now_playing_song_info)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
					)
					SettingsChoiceItem(
						content = { Text(stringResource(R.string.option_now_playing_toolbar_position)) },
						choices = ToolbarPosition.entries.toImmutableList(),
						selectedChoice = preferenceManager.nowPlayingToolbarPosition,
						onChoiceSelected = { preferenceManager.nowPlayingToolbarPosition = it },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2)
					)
				}
			}
		}
	}

	NowPlayingSliderStyleDialog(
		presented = sliderStyleDialogOpen,
		onDismissRequest = { sliderStyleDialogOpen = false }
	)
}

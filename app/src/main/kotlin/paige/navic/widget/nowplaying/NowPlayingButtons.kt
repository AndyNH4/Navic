/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.nowplaying

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.components.CircleIconButton
import paige.navic.R
import paige.navic.widget.action.WidgetCommands
import paige.navic.widget.action.widgetPlaybackAction
import paige.navic.widget.state.WidgetSnapshot
import androidx.media3.common.Player

@Composable
fun WidgetPlayPauseButton(snapshot: WidgetSnapshot, modifier: GlanceModifier = GlanceModifier) {
	if (snapshot.pendingCommand == WidgetCommands.PLAY_PAUSE) {
		CircularProgressIndicator(modifier)
		return
	}
	val showPause = snapshot.isPlaying || (snapshot.playWhenReady && snapshot.isBuffering)
	val context = LocalContext.current
	CircleIconButton(
		imageProvider = ImageProvider(if (showPause) R.drawable.ic_pause else R.drawable.ic_play),
		contentDescription = context.getString(if (showPause) R.string.action_pause else R.string.action_play),
		contentColor = GlanceTheme.colors.onPrimaryContainer,
		onClick = widgetPlaybackAction(WidgetCommands.PLAY_PAUSE),
		enabled = snapshot.canPlay,
		backgroundColor = null,
		modifier = modifier
	)
}

@Composable
fun WidgetPreviousButton(snapshot: WidgetSnapshot, modifier: GlanceModifier = GlanceModifier) {
	val context = LocalContext.current
	if (snapshot.pendingCommand == WidgetCommands.PREVIOUS) {
		CircularProgressIndicator(modifier)
		return
	}
	CircleIconButton(
		imageProvider = ImageProvider(R.drawable.ic_previous),
		contentDescription = context.getString(R.string.action_previous_song),
		contentColor = GlanceTheme.colors.onPrimaryContainer,
		onClick = widgetPlaybackAction(WidgetCommands.PREVIOUS),
		enabled = snapshot.canPrevious,
		backgroundColor = null,
		modifier = modifier
	)
}

@Composable
fun WidgetNextButton(snapshot: WidgetSnapshot, modifier: GlanceModifier = GlanceModifier) {
	val context = LocalContext.current
	if (snapshot.pendingCommand == WidgetCommands.NEXT) {
		CircularProgressIndicator(modifier)
		return
	}
	CircleIconButton(
		imageProvider = ImageProvider(R.drawable.ic_next),
		contentDescription = context.getString(R.string.action_next_song),
		contentColor = GlanceTheme.colors.onPrimaryContainer,
		onClick = widgetPlaybackAction(WidgetCommands.NEXT),
		enabled = snapshot.canNext,
		backgroundColor = null,
		modifier = modifier
	)
}

@Composable
fun WidgetStarButton(snapshot: WidgetSnapshot, modifier: GlanceModifier = GlanceModifier) {
	val context = LocalContext.current
	if (snapshot.pendingCommand == WidgetCommands.TOGGLE_STAR) {
		CircularProgressIndicator(modifier)
		return
	}
	CircleIconButton(
		imageProvider = ImageProvider(if (snapshot.isStarred) R.drawable.ic_star_filled else R.drawable.ic_star),
		contentDescription = context.getString(
			if (snapshot.isStarred) R.string.action_remove_star else R.string.action_star
		),
		contentColor = if (snapshot.isStarred) GlanceTheme.colors.onTertiary else GlanceTheme.colors.onPrimaryContainer,
		onClick = widgetPlaybackAction(WidgetCommands.TOGGLE_STAR),
		enabled = snapshot.canStar,
		backgroundColor = if (snapshot.isStarred) GlanceTheme.colors.tertiary else null,
		modifier = modifier
	)
}

@Composable
fun WidgetShuffleButton(snapshot: WidgetSnapshot, modifier: GlanceModifier = GlanceModifier) {
	val context = LocalContext.current
	if (snapshot.pendingCommand == WidgetCommands.SHUFFLE) {
		CircularProgressIndicator(modifier)
		return
	}
	CircleIconButton(
		imageProvider = ImageProvider(if (snapshot.isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle),
		contentDescription = context.getString(R.string.action_shuffle),
		contentColor = if (snapshot.isShuffleEnabled) GlanceTheme.colors.primary else GlanceTheme.colors.onPrimaryContainer,
		onClick = widgetPlaybackAction(WidgetCommands.SHUFFLE),
		enabled = snapshot.canShuffle,
		backgroundColor = null,
		modifier = modifier
	)
}

@Composable
fun WidgetRepeatButton(snapshot: WidgetSnapshot, modifier: GlanceModifier = GlanceModifier) {
	val context = LocalContext.current
	if (snapshot.pendingCommand == WidgetCommands.REPEAT) {
		CircularProgressIndicator(modifier)
		return
	}
	val icon = when (snapshot.repeatMode) {
		Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one_on
		Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_on
		else -> R.drawable.ic_repeat
	}
	val description = when (snapshot.repeatMode) {
		Player.REPEAT_MODE_ONE -> R.string.info_repeat_one
		Player.REPEAT_MODE_ALL -> R.string.info_repeat_all
		else -> R.string.info_repeat_off
	}
	CircleIconButton(
		imageProvider = ImageProvider(icon),
		contentDescription = context.getString(description),
		contentColor = if (snapshot.repeatMode == Player.REPEAT_MODE_OFF) {
			GlanceTheme.colors.onPrimaryContainer
		} else {
			GlanceTheme.colors.primary
		},
		onClick = widgetPlaybackAction(WidgetCommands.REPEAT),
		enabled = snapshot.canRepeat,
		backgroundColor = null,
		modifier = modifier
	)
}

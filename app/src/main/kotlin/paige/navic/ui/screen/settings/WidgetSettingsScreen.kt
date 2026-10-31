/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.domain.manager.SnackBarManager
import paige.navic.ui.component.common.SegmentedListItem
import paige.navic.ui.component.common.SegmentedListItemDefaults
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.screen.settings.component.SettingsGroup
import paige.navic.widget.miniplayer.MiniPlayerReceiver
import paige.navic.widget.miniplayer.MiniPlayerWidget
import paige.navic.widget.queue.QueueReceiver
import paige.navic.widget.queue.QueueWidget
import paige.navic.widget.quickplay.QuickPlayReceiver
import paige.navic.widget.quickplay.QuickPlayWidget
import paige.navic.widget.turntable.TurnTableReceiver
import paige.navic.widget.turntable.TurnTableWidget

@Composable
fun WidgetSettingsScreen() {
	val context = LocalContext.current
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()
	fun pin(receiver: Class<out GlanceAppWidgetReceiver>, widget: GlanceAppWidget) {
		scope.launch {
			val added = runCatching {
				GlanceAppWidgetManager(context).requestPinGlanceAppWidget(receiver, widget)
			}.getOrDefault(false)
			if (!added) snackBarManager.notify(R.string.widget_pin_unsupported)
		}
	}

	Scaffold(
		topBar = { NestedTopBar({ Text(stringResource(R.string.title_widgets)) }) }
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(16.dp)
		) {
			Text(
				text = stringResource(R.string.widget_settings_description),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
			)
			SettingsGroup {
				WidgetPinRow(
					title = R.string.widget_mini_player,
					subtitle = R.string.widget_mini_player_description,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 4),
					onClick = { pin(MiniPlayerReceiver::class.java, MiniPlayerWidget()) }
				)
				WidgetPinRow(
					title = R.string.widget_turn_table,
					subtitle = R.string.widget_turn_table_description,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 4),
					onClick = { pin(TurnTableReceiver::class.java, TurnTableWidget()) }
				)
				WidgetPinRow(
					title = R.string.widget_playback_queue,
					subtitle = R.string.widget_playback_queue_description,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 4),
					onClick = { pin(QueueReceiver::class.java, QueueWidget()) }
				)
				WidgetPinRow(
					title = R.string.widget_quick_play,
					subtitle = R.string.widget_quick_play_description,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 4),
					onClick = { pin(QuickPlayReceiver::class.java, QuickPlayWidget()) }
				)
			}
		}
	}
}

@Composable
private fun WidgetPinRow(
	title: Int,
	subtitle: Int,
	shapes: ListItemShapes,
	onClick: () -> Unit
) {
	SegmentedListItem(
		shapes = shapes,
		onClick = dropUnlessResumed { onClick() },
		contentPadding = PaddingValues(16.dp)
	) {
		Column {
			Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
			Text(
				stringResource(subtitle),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}

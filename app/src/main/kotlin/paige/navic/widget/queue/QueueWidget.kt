/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.queue

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import paige.navic.R
import paige.navic.domain.manager.WidgetNavigationTarget
import paige.navic.util.appWidgetInnerCornerRadius
import paige.navic.widget.action.widgetQueueItemAction
import paige.navic.widget.nowplaying.NowPlayingWidget
import paige.navic.widget.nowplaying.WidgetNextButton
import paige.navic.widget.nowplaying.WidgetPlayPauseButton
import paige.navic.widget.state.WidgetSnapshot
import paige.navic.widget.widgetNavigationIntent

class QueueWidget : NowPlayingWidget() {
	override val sizeMode = SizeMode.Responsive(
		setOf(
			DpSize(250.dp, 160.dp),
			DpSize(300.dp, 240.dp),
			DpSize(330.dp, 320.dp)
		)
	)

	@Composable
	override fun Content(context: Context, snapshot: WidgetSnapshot, bitmap: Bitmap?) {
		Column(
			modifier = GlanceModifier
				.fillMaxSize()
				.background(GlanceTheme.colors.widgetBackground)
				.appWidgetInnerCornerRadius(0.dp)
				.appWidgetBackground()
		) {
			Row(
				modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Image(
					provider = bitmap?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_note),
					contentDescription = null,
					contentScale = ContentScale.Crop,
					modifier = GlanceModifier.size(32.dp).appWidgetInnerCornerRadius(6.dp)
				)
				Text(
					text = context.getString(R.string.action_queue),
					style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 16.sp),
					maxLines = 1,
					modifier = GlanceModifier.defaultWeight().padding(horizontal = 8.dp)
				)
				WidgetPlayPauseButton(snapshot, GlanceModifier.size(38.dp))
				WidgetNextButton(snapshot, GlanceModifier.size(38.dp))
				CircleIconButton(
					imageProvider = ImageProvider(R.drawable.ic_queue),
					contentDescription = context.getString(R.string.action_queue),
					contentColor = GlanceTheme.colors.onPrimaryContainer,
					onClick = actionStartActivity(
						widgetNavigationIntent(context, WidgetNavigationTarget.Queue)
					),
					backgroundColor = null,
					modifier = GlanceModifier.size(38.dp)
				)
			}
			if (snapshot.queue.isEmpty()) {
				Text(
					text = context.getString(R.string.info_no_queue),
					style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 14.sp),
					modifier = GlanceModifier.padding(16.dp)
				)
			} else {
				LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
					items(snapshot.queue) { item ->
						Row(
							modifier = GlanceModifier
								.fillMaxWidth()
								.clickable(widgetQueueItemAction(item, snapshot.revision))
								.padding(horizontal = 12.dp, vertical = 7.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(
								text = "${item.index + 1}",
								style = TextStyle(
									color = if (item.isCurrent) GlanceTheme.colors.primary else GlanceTheme.colors.onPrimaryContainer,
									fontSize = 12.sp
								),
								modifier = GlanceModifier.padding(end = 10.dp)
							)
							Column(modifier = GlanceModifier.defaultWeight()) {
								Text(
									text = item.title.ifBlank { context.getString(R.string.info_unknown) },
									style = TextStyle(
										color = if (item.isCurrent) GlanceTheme.colors.primary else GlanceTheme.colors.onPrimaryContainer,
										fontSize = 14.sp
									),
									maxLines = 1
								)
								item.artist?.takeIf { it.isNotBlank() }?.let { artist ->
									Text(
										text = artist,
										style = TextStyle(
											color = GlanceTheme.colors.onPrimaryContainer,
											fontSize = 12.sp
										),
										maxLines = 1
									)
								}
							}
						}
					}
				}
			}
		}
	}
}

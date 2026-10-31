/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.turntable

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
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import paige.navic.util.appWidgetInnerCornerRadius
import paige.navic.widget.nowplaying.NowPlayingWidget
import paige.navic.widget.nowplaying.WidgetNextButton
import paige.navic.widget.nowplaying.WidgetPlayPauseButton
import paige.navic.widget.nowplaying.WidgetPreviousButton
import paige.navic.widget.nowplaying.WidgetRepeatButton
import paige.navic.widget.nowplaying.WidgetShuffleButton
import paige.navic.widget.nowplaying.WidgetStarButton
import paige.navic.widget.state.WidgetSnapshot

class TurnTableWidget : NowPlayingWidget() {
	override val sizeMode = SizeMode.Responsive(
		setOf(DpSize(140.dp, 140.dp), DpSize(220.dp, 220.dp))
	)

	@Composable
	override fun Content(context: Context, snapshot: WidgetSnapshot, bitmap: Bitmap?) {
		val size = LocalSize.current
		val expanded = minOf(size.width, size.height) >= 200.dp
		Box(
			modifier = GlanceModifier
				.size(minOf(size.width, size.height))
				.background(GlanceTheme.colors.widgetBackground)
				.clickable(actionStartActivity(launchIntent(context)))
				.appWidgetInnerCornerRadius(0.dp)
				.appWidgetBackground()
		) {
			Image(
				provider = bitmap?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_note),
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = GlanceModifier
					.fillMaxSize()
					.background(GlanceTheme.colors.primaryContainer)
					.cornerRadius(300.dp)
			)
			Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
				Row(
					modifier = GlanceModifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically
				) {
					if (expanded) {
						WidgetShuffleButton(snapshot, GlanceModifier.size(40.dp))
						WidgetRepeatButton(snapshot, GlanceModifier.size(40.dp))
					}
					Box(modifier = GlanceModifier.defaultWeight()) {}
					WidgetStarButton(snapshot, GlanceModifier.size(if (expanded) 44.dp else 40.dp))
				}
				Box(
					modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
					contentAlignment = Alignment.Center
				) {
					if (expanded) {
						Column(
							modifier = GlanceModifier
								.background(GlanceTheme.colors.widgetBackground)
								.appWidgetInnerCornerRadius(4.dp)
								.padding(horizontal = 8.dp, vertical = 4.dp),
							horizontalAlignment = Alignment.CenterHorizontally
						) {
							Text(
								text = snapshot.title?.takeIf { it.isNotBlank() }
									?: context.getString(R.string.info_not_playing),
								style = TextStyle(
									color = GlanceTheme.colors.onPrimaryContainer,
									fontSize = 15.sp
								),
								maxLines = 1
							)
							Text(
								text = snapshot.artist?.takeIf { it.isNotBlank() }
									?: context.getString(R.string.info_no_artists),
								style = TextStyle(
									color = GlanceTheme.colors.onPrimaryContainer,
									fontSize = 12.sp
								),
								maxLines = 1
							)
						}
					}
				}
				Row(
					modifier = GlanceModifier.fillMaxWidth(),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalAlignment = Alignment.CenterVertically
				) {
					WidgetPreviousButton(snapshot, GlanceModifier.size(40.dp))
					WidgetPlayPauseButton(snapshot, GlanceModifier.size(if (expanded) 52.dp else 46.dp))
					WidgetNextButton(snapshot, GlanceModifier.size(40.dp))
				}
			}
		}
	}
}

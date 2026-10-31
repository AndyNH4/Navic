/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.miniplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
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
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import paige.navic.R
import paige.navic.util.appWidgetInnerCornerRadius
import paige.navic.widget.nowplaying.NowPlayingWidget
import paige.navic.widget.nowplaying.WidgetNextButton
import paige.navic.widget.nowplaying.WidgetPlayPauseButton
import paige.navic.widget.nowplaying.WidgetPreviousButton
import paige.navic.widget.state.WidgetArtworkLoader
import paige.navic.widget.state.WidgetSnapshot

class MiniPlayerWidget : NowPlayingWidget() {
	override val sizeMode = SizeMode.Exact
	override val artworkSizePx = WidgetArtworkLoader.SMALL_SIZE

	@SuppressLint("RestrictedApi")
	@Composable
	override fun Content(context: Context, snapshot: WidgetSnapshot, bitmap: Bitmap?) {
		Box(
			modifier = GlanceModifier.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			Row(
				modifier = GlanceModifier
					.background(GlanceTheme.colors.widgetBackground)
					.fillMaxSize()
					.height(88.dp)
					.padding(12.dp)
					.clickable(actionStartActivity(launchIntent(context)))
					.appWidgetInnerCornerRadius(0.dp)
					.appWidgetBackground(),
				verticalAlignment = Alignment.CenterVertically
			) {
				Image(
					provider = bitmap?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_note),
					contentDescription = null,
					contentScale = ContentScale.Crop,
					modifier = GlanceModifier
						.size(64.dp)
						.background(GlanceTheme.colors.primaryContainer)
						.appWidgetInnerCornerRadius(12.dp)
				)
				Column(modifier = GlanceModifier.defaultWeight().padding(horizontal = 12.dp)) {
					Text(
						text = snapshot.title?.takeIf { it.isNotBlank() }
							?: context.getString(R.string.info_not_playing),
						style = TextStyle(
							color = GlanceTheme.colors.onPrimaryContainer,
							fontSize = 16.sp
						),
						maxLines = 1
					)
					val artist = snapshot.artist?.takeIf { it.isNotBlank() }
						?: context.getString(R.string.info_no_artists)
					Text(
						text = if (snapshot.isBuffering) {
							context.getString(R.string.widget_buffering, artist)
						} else {
							artist
						},
						style = TextStyle(
							color = ColorProvider(
								GlanceTheme.colors.onPrimaryContainer.getColor(context).copy(alpha = .8f)
							),
							fontSize = 14.sp
						),
						maxLines = 1
					)
				}
				WidgetPreviousButton(snapshot)
				WidgetPlayPauseButton(snapshot)
				WidgetNextButton(snapshot)
			}
		}
	}
}

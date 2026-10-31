/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.quickplay

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import paige.navic.R
import paige.navic.domain.manager.WidgetNavigationTarget
import paige.navic.util.appWidgetInnerCornerRadius
import paige.navic.widget.state.WidgetArtworkLoader
import paige.navic.widget.state.WidgetSnapshotStore
import paige.navic.widget.widgetNavigationIntent

class QuickPlayWidget : GlanceAppWidget(), KoinComponent {
	override val stateDefinition = PreferencesGlanceStateDefinition
	override val sizeMode = SizeMode.Responsive(
		setOf(DpSize(140.dp, 64.dp), DpSize(220.dp, 140.dp))
	)
	private val repository: QuickPlayRepository by inject()
	private val artworkLoader: WidgetArtworkLoader by inject()
	private val snapshotStore: WidgetSnapshotStore by inject()

	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val initialSnapshot = snapshotStore.current()
		provideContent {
			val prefs = currentState<Preferences>()
			val type = QuickPlaySourceType.from(prefs[QuickPlayKeys.SOURCE_TYPE])
			val sourceId = prefs[QuickPlayKeys.SOURCE_ID]?.takeIf { it.isNotBlank() }
			val savedName = prefs[QuickPlayKeys.SOURCE_NAME]
			val pending = prefs[QuickPlayKeys.PENDING] == true
			val failed = prefs[QuickPlayKeys.ERROR] == true
			val playbackSnapshot by produceState(initialValue = initialSnapshot) {
				snapshotStore.state.collect { value = it }
			}
			val info by produceState<QuickPlaySourceInfo?>(null, type, sourceId) {
				value = runCatching { repository.getSourceInfo(type, sourceId) }.getOrNull()
			}
			val artworkUri = if (type == QuickPlaySourceType.CONTINUE) {
				playbackSnapshot.artworkUri
			} else {
				info?.artworkUri
			}
			val bitmap by produceState<Bitmap?>(null, artworkUri) {
				value = artworkLoader.load(artworkUri, WidgetArtworkLoader.SMALL_SIZE)
			}
			val title = info?.title ?: savedName ?: context.getString(R.string.widget_continue)
			val subtitle = when {
				failed -> context.getString(R.string.widget_unavailable)
				type == QuickPlaySourceType.CONTINUE && playbackSnapshot.hasContent -> {
					playbackSnapshot.title ?: playbackSnapshot.artist.orEmpty()
				}
				info != null -> info?.subtitle.orEmpty()
				else -> context.getString(R.string.widget_loading)
			}
			val available = if (type == QuickPlaySourceType.CONTINUE) {
				playbackSnapshot.hasContent || info?.available == true
			} else {
				info?.available == true
			}
			val expanded = LocalSize.current.width >= 200.dp
			GlanceTheme {
				Row(
					modifier = GlanceModifier
						.fillMaxSize()
						.background(GlanceTheme.colors.widgetBackground)
						.clickable(actionStartActivity(widgetNavigationIntent(context, navigationTarget(type, sourceId))))
						.appWidgetInnerCornerRadius(0.dp)
						.appWidgetBackground()
						.padding(if (expanded) 16.dp else 8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Image(
						provider = bitmap?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_note),
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = GlanceModifier
							.size(if (expanded) 64.dp else 40.dp)
							.background(GlanceTheme.colors.primaryContainer)
							.appWidgetInnerCornerRadius(10.dp)
					)
					Column(modifier = GlanceModifier.defaultWeight().padding(horizontal = 10.dp)) {
						Text(
							text = title,
							style = TextStyle(
								color = GlanceTheme.colors.onPrimaryContainer,
								fontSize = if (expanded) 17.sp else 13.sp
							),
							maxLines = 1
						)
						Text(
							text = subtitle,
							style = TextStyle(
								color = GlanceTheme.colors.onPrimaryContainer,
								fontSize = if (expanded) 14.sp else 11.sp
							),
							maxLines = 2
						)
					}
					if (pending) {
						CircularProgressIndicator(GlanceModifier.size(40.dp))
					} else {
						CircleIconButton(
							imageProvider = ImageProvider(R.drawable.ic_play),
							contentDescription = context.getString(R.string.action_play),
							contentColor = GlanceTheme.colors.onPrimaryContainer,
							onClick = quickPlayAction(type, sourceId),
							enabled = available,
							backgroundColor = null,
							modifier = GlanceModifier.size(if (expanded) 48.dp else 38.dp)
						)
					}
				}
			}
		}
	}

	private fun navigationTarget(type: QuickPlaySourceType, id: String?): WidgetNavigationTarget =
		when (type) {
			QuickPlaySourceType.CONTINUE -> WidgetNavigationTarget.NowPlaying
			QuickPlaySourceType.RANDOM -> WidgetNavigationTarget.Library
			QuickPlaySourceType.FAVORITES -> WidgetNavigationTarget.Starred
			QuickPlaySourceType.DOWNLOADS -> WidgetNavigationTarget.Library
			QuickPlaySourceType.PLAYLIST -> id?.let {
				WidgetNavigationTarget.Collection(it, "playlists")
			} ?: WidgetNavigationTarget.PlaylistList
			QuickPlaySourceType.RADIO -> WidgetNavigationTarget.RadioList
		}
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.nowplaying

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.state.PreferencesGlanceStateDefinition
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import paige.navic.widget.state.WidgetArtworkLoader
import paige.navic.widget.state.WidgetSnapshot
import paige.navic.widget.state.WidgetSnapshotStore
import paige.navic.widget.widgetNavigationIntent

abstract class NowPlayingWidget : GlanceAppWidget(), KoinComponent {
	override val stateDefinition = PreferencesGlanceStateDefinition
	private val snapshotStore: WidgetSnapshotStore by inject()
	private val artworkLoader: WidgetArtworkLoader by inject()
	protected open val artworkSizePx: Int = WidgetArtworkLoader.LARGE_SIZE

	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val initialSnapshot = snapshotStore.current()
		provideContent {
			val snapshot by produceState(initialValue = initialSnapshot) {
				snapshotStore.state.collect { value = it }
			}
			val bitmap by produceState<Bitmap?>(
				initialValue = null,
				snapshot.artworkUri
			) {
				value = artworkLoader.load(snapshot.artworkUri, artworkSizePx)
			}

			GlanceTheme {
				Content(context = context, snapshot = snapshot, bitmap = bitmap)
			}
		}
	}

	@Composable
	abstract fun Content(context: Context, snapshot: WidgetSnapshot, bitmap: Bitmap?)

	protected fun launchIntent(context: Context) = widgetNavigationIntent(context)
}

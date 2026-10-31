/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.nowplaying

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

open class NowPlayingReceiver(
	private val widgetClass: Class<out NowPlayingWidget>
) : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget by lazy {
		widgetClass.getDeclaredConstructor().newInstance()
	}
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.quickplay

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class QuickPlayReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = QuickPlayWidget()
}

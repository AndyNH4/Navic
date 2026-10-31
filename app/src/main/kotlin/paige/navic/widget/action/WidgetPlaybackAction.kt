/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.action

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WidgetPlaybackAction : ActionCallback, KoinComponent {
	private val handler: WidgetActionHandler by inject()

	override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
		val command = parameters[COMMAND_KEY] ?: return
		handler.execute(command)
	}

	companion object {
		val COMMAND_KEY = ActionParameters.Key<String>("command")
	}
}

fun widgetPlaybackAction(command: String): Action = actionRunCallback<WidgetPlaybackAction>(
	actionParametersOf(WidgetPlaybackAction.COMMAND_KEY to command)
)

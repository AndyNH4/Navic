/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.quickplay

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import paige.navic.util.Logger

class QuickPlayAction : ActionCallback, KoinComponent {
	private val handler: QuickPlayActionHandler by inject()

	override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
		val type = QuickPlaySourceType.from(parameters[TYPE_KEY])
		val id = parameters[ID_KEY]?.takeIf { it.isNotBlank() }
		val widget = QuickPlayWidget()
		updateAppWidgetState(context, glanceId) { prefs ->
			prefs[QuickPlayKeys.PENDING] = true
			prefs[QuickPlayKeys.ERROR] = false
		}
		widget.update(context, glanceId)
		var failed = false
		try {
			val completed = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { handler.play(type, id) }
			if (completed == null) throw IllegalStateException("Quick play timed out")
		} catch (error: Exception) {
			failed = true
			Logger.w(TAG, "quick play failed", error)
		} finally {
			updateAppWidgetState(context, glanceId) { prefs ->
				prefs[QuickPlayKeys.PENDING] = false
				prefs[QuickPlayKeys.ERROR] = failed
			}
			widget.update(context, glanceId)
		}
	}

	companion object {
		val TYPE_KEY = ActionParameters.Key<String>("quick_play_type")
		val ID_KEY = ActionParameters.Key<String>("quick_play_id")
		private const val TAG = "QuickPlayAction"
		private const val COMMAND_TIMEOUT_MS = 8_000L
	}
}

fun quickPlayAction(type: QuickPlaySourceType, id: String? = null): Action =
	actionRunCallback<QuickPlayAction>(
		actionParametersOf(
			QuickPlayAction.TYPE_KEY to type.name,
			QuickPlayAction.ID_KEY to id.orEmpty()
		)
	)

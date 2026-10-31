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
import paige.navic.widget.state.WidgetQueueItem

class WidgetQueueItemAction : ActionCallback, KoinComponent {
	private val handler: WidgetActionHandler by inject()

	override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
		val mediaId = parameters[MEDIA_ID_KEY] ?: return
		val index = parameters[INDEX_KEY] ?: return
		val revision = parameters[REVISION_KEY] ?: return
		handler.executeQueueItem(mediaId, index, revision)
	}

	companion object {
		val MEDIA_ID_KEY = ActionParameters.Key<String>("queue_media_id")
		val INDEX_KEY = ActionParameters.Key<Int>("queue_index")
		val REVISION_KEY = ActionParameters.Key<Long>("queue_revision")
	}
}

fun widgetQueueItemAction(item: WidgetQueueItem, revision: Long): Action =
	actionRunCallback<WidgetQueueItemAction>(
		actionParametersOf(
			WidgetQueueItemAction.MEDIA_ID_KEY to item.mediaId,
			WidgetQueueItemAction.INDEX_KEY to item.index,
			WidgetQueueItemAction.REVISION_KEY to revision
		)
	)

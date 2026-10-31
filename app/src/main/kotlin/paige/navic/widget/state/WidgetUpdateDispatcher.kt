/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.state

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import paige.navic.util.Logger
import paige.navic.widget.miniplayer.MiniPlayerWidget
import paige.navic.widget.queue.QueueWidget
import paige.navic.widget.turntable.TurnTableWidget

class WidgetUpdateDispatcher(
	private val context: Context
) {
	private val miniPlayerWidget = MiniPlayerWidget()
	private val turnTableWidget = TurnTableWidget()
	private val queueWidget = QueueWidget()

	suspend fun updateNowPlaying() = coroutineScope {
		launch(Dispatchers.Default) {
			runCatching { miniPlayerWidget.updateAll(context) }
				.onFailure { Logger.w(TAG, "failed to update mini player widgets", it) }
		}
		launch(Dispatchers.Default) {
			runCatching { turnTableWidget.updateAll(context) }
				.onFailure { Logger.w(TAG, "failed to update turn table widgets", it) }
		}
		launch(Dispatchers.Default) {
			runCatching { queueWidget.updateAll(context) }
				.onFailure { Logger.w(TAG, "failed to update queue widgets", it) }
		}
	}

	private companion object {
		const val TAG = "WidgetUpdateDispatcher"
	}
}

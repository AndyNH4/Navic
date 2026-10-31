/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface WidgetNavigationTarget {
	data object NowPlaying : WidgetNavigationTarget
	data object Queue : WidgetNavigationTarget
	data object Starred : WidgetNavigationTarget
	data object PlaylistList : WidgetNavigationTarget
	data object RadioList : WidgetNavigationTarget
	data object Library : WidgetNavigationTarget
	data class Collection(val id: String, val tab: String = "") : WidgetNavigationTarget
}

class WidgetNavigationManager {
	val pendingTarget: StateFlow<WidgetNavigationTarget?>
		field = MutableStateFlow(null)

	fun request(target: WidgetNavigationTarget) {
		pendingTarget.value = target
	}

	fun consume() {
		pendingTarget.value = null
	}

	fun handleIntent(intent: Intent?): Boolean {
		if (intent?.action != ACTION_WIDGET_NAVIGATION) return false
		val name = intent.getStringExtra(EXTRA_TARGET)
		val target = when (name) {
			TARGET_NOW_PLAYING -> WidgetNavigationTarget.NowPlaying
			TARGET_QUEUE -> WidgetNavigationTarget.Queue
			TARGET_STARRED -> WidgetNavigationTarget.Starred
			TARGET_PLAYLISTS -> WidgetNavigationTarget.PlaylistList
			TARGET_RADIOS -> WidgetNavigationTarget.RadioList
			TARGET_LIBRARY -> WidgetNavigationTarget.Library
			TARGET_COLLECTION -> intent.getStringExtra(EXTRA_COLLECTION_ID)
				?.let { WidgetNavigationTarget.Collection(it, intent.getStringExtra(EXTRA_COLLECTION_TAB).orEmpty()) }
			else -> null
		} ?: return false
		request(target)
		intent.action = null
		intent.removeExtra(EXTRA_TARGET)
		return true
	}

	companion object {
		const val ACTION_WIDGET_NAVIGATION = "paige.navic.action.WIDGET_NAVIGATION"
		const val EXTRA_TARGET = "widget_target"
		const val EXTRA_COLLECTION_ID = "widget_collection_id"
		const val EXTRA_COLLECTION_TAB = "widget_collection_tab"

		const val TARGET_NOW_PLAYING = "now_playing"
		const val TARGET_QUEUE = "queue"
		const val TARGET_STARRED = "starred"
		const val TARGET_PLAYLISTS = "playlists"
		const val TARGET_RADIOS = "radios"
		const val TARGET_LIBRARY = "library"
		const val TARGET_COLLECTION = "collection"
	}
}

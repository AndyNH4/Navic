/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget

import android.content.Context
import android.content.Intent
import paige.navic.MainActivity
import paige.navic.domain.manager.WidgetNavigationManager
import paige.navic.domain.manager.WidgetNavigationTarget

fun widgetNavigationIntent(
	context: Context,
	target: WidgetNavigationTarget = WidgetNavigationTarget.NowPlaying
): Intent {
	val intent = Intent(context, MainActivity::class.java)
		.setAction(WidgetNavigationManager.ACTION_WIDGET_NAVIGATION)
		.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
	when (target) {
		WidgetNavigationTarget.NowPlaying -> WidgetNavigationManager.TARGET_NOW_PLAYING
		WidgetNavigationTarget.Queue -> WidgetNavigationManager.TARGET_QUEUE
		WidgetNavigationTarget.Starred -> WidgetNavigationManager.TARGET_STARRED
		WidgetNavigationTarget.PlaylistList -> WidgetNavigationManager.TARGET_PLAYLISTS
		WidgetNavigationTarget.RadioList -> WidgetNavigationManager.TARGET_RADIOS
		WidgetNavigationTarget.Library -> WidgetNavigationManager.TARGET_LIBRARY
		is WidgetNavigationTarget.Collection -> WidgetNavigationManager.TARGET_COLLECTION
	}.let { intent.putExtra(WidgetNavigationManager.EXTRA_TARGET, it) }
	if (target is WidgetNavigationTarget.Collection) {
		intent.putExtra(WidgetNavigationManager.EXTRA_COLLECTION_ID, target.id)
		intent.putExtra(WidgetNavigationManager.EXTRA_COLLECTION_TAB, target.tab)
	}
	return intent
}

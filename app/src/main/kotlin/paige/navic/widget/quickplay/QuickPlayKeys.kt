/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.quickplay

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object QuickPlayKeys {
	val SOURCE_TYPE = stringPreferencesKey("quick_play_source_type")
	val SOURCE_ID = stringPreferencesKey("quick_play_source_id")
	val SOURCE_NAME = stringPreferencesKey("quick_play_source_name")
	val PENDING = booleanPreferencesKey("quick_play_pending")
	val ERROR = booleanPreferencesKey("quick_play_error")
}

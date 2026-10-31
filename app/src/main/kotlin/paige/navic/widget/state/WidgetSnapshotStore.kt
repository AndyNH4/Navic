/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.state

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import paige.navic.util.Logger

class WidgetSnapshotStore(
	private val preferences: DataStore<Preferences>
) {
	private val json = Json { ignoreUnknownKeys = true }

	val state: Flow<WidgetSnapshot> = preferences.data
		.map { decode(it[KEY_SNAPSHOT]) }
		.distinctUntilChanged()

	suspend fun current(): WidgetSnapshot = state.first()

	suspend fun update(
		transform: (WidgetSnapshot) -> WidgetSnapshot
	): WidgetSnapshot? {
		var updated: WidgetSnapshot? = null
		preferences.edit { prefs ->
			val current = decode(prefs[KEY_SNAPSHOT])
			val candidate = transform(current)
			if (candidate != current) {
				val next = candidate.copy(revision = current.revision + 1)
				prefs[KEY_SNAPSHOT] = json.encodeToString(next)
				updated = next
			}
		}
		return updated
	}

	suspend fun setPending(command: String): WidgetSnapshot? = update {
		it.copy(pendingCommand = command)
	}

	suspend fun clearPending(command: String? = null): WidgetSnapshot? = update { current ->
		if (command == null || current.pendingCommand == command) {
			current.copy(pendingCommand = null)
		} else {
			current
		}
	}

	private fun decode(value: String?): WidgetSnapshot {
		if (value == null) return WidgetSnapshot()
		return try {
			json.decodeFromString<WidgetSnapshot>(value)
		} catch (error: Exception) {
			Logger.e(TAG, "failed to read widget snapshot", error)
			WidgetSnapshot()
		}
	}

	private companion object {
		const val TAG = "WidgetSnapshotStore"
		val KEY_SNAPSHOT = stringPreferencesKey("snapshot")
	}
}

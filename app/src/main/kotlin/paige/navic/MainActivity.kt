/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.android.ext.android.inject
import paige.navic.domain.manager.VoiceActionManager

class MainActivity : ComponentActivity() {
	private val voiceActionManager: VoiceActionManager by inject()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		handleVoiceIntent(intent)
		setContent { App() }
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleVoiceIntent(intent)
	}

	private fun handleVoiceIntent(intent: Intent?) {
		if (voiceActionManager.handleIntent(intent)) {
			// strip the data so a recreation (rotation, config change) doesn't
			// re-dispatch the same voice action
			intent?.data = null
		}
	}
}

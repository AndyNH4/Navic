/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.exoplayer.impl

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import paige.navic.domain.manager.EqualizerManager
import paige.navic.domain.model.settings.EqualizerConfig
import paige.navic.domain.model.settings.EqualizerMode
import paige.navic.util.Logger

/**
 * Applies the equalizer configuration to every crossfade-engine leg.
 *
 * Each leg owns a distinct audio session for its whole lifetime (Media3
 * generates the session id once per player, stable until release), so effects
 * are keyed by session id: a session that appears gets an effect built from
 * the current config; a session that goes away (leg released / detached / id
 * changed) gets it released. During a fade both legs therefore stay equalized
 * and the outgoing track's tail never loses EQ mid-ramp; a stopped-but-alive
 * standby leg keeps its effect so re-arming it needs no re-attach.
 *
 * All methods run on the main thread (player listener callbacks + service).
 */
@UnstableApi
class ExoEqualizerManager(
	private val equalizerManager: EqualizerManager,
	private val context: Context
) {
	private var equalizerMode = EqualizerMode.Disabled
	private val scope = CoroutineScope(Dispatchers.Default)

	/** audio session id -> built-in equalizer attached to that session */
	private val equalizers = mutableMapOf<Int, Equalizer>()

	/** sessions currently announced to external equalizer apps */
	private val openExternalSessions = mutableSetOf<Int>()

	/** leg -> its latest audio session id, so a departing session can be released */
	private val legSessions = mutableMapOf<Player, Int>()

	/** leg -> listener registered on it, for detach */
	private val legListeners = mutableMapOf<Player, Player.Listener>()

	/** Hooks the manager into one engine leg; safe to call when a new leg appears. */
	fun attachLeg(player: Player) {
		if (legListeners.containsKey(player)) return
		val listener = object : Player.Listener {
			override fun onAudioSessionIdChanged(audioSessionId: Int) {
				onLegAudioSessionChanged(player, audioSessionId)
			}
		}
		legListeners[player] = listener
		player.addListener(listener)
		// The session id is generated asynchronously after construction, so the
		// listener above normally observes it; read it anyway in case this leg
		// already had one assigned before attaching.
		onLegAudioSessionChanged(player, player.audioSessionId)
	}

	/** Unhooks the manager from a leg and releases the effect on its session. */
	fun detachLeg(player: Player) {
		legListeners.remove(player)?.let(player::removeListener)
		val sessionId = legSessions.remove(player) ?: return
		onSessionEnded(sessionId)
	}

	private fun onLegAudioSessionChanged(player: Player, audioSessionId: Int) {
		val previous = legSessions.put(player, audioSessionId)
		if (previous == audioSessionId) return
		if (previous != null) onSessionEnded(previous)
		if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) onSessionStarted(audioSessionId)
	}

	private fun onSessionStarted(sessionId: Int) {
		when (equalizerMode) {
			EqualizerMode.BuiltIn -> createEqualizer(sessionId)
			EqualizerMode.External -> openAudioEffectSession(sessionId)
			EqualizerMode.Disabled -> Unit
		}
	}

	private fun onSessionEnded(sessionId: Int) {
		if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
		// legs own distinct sessions, but never tear down an effect another
		// leg still references
		if (sessionId in legSessions.values) return
		equalizers.remove(sessionId)?.let { equalizer ->
			runCatching { equalizer.release() }
		}
		closeAudioEffectSession(sessionId)
	}

	private fun createEqualizer(sessionId: Int) {
		if (sessionId == C.AUDIO_SESSION_ID_UNSET || equalizers.containsKey(sessionId)) return
		try {
			val equalizer = Equalizer(0, sessionId).apply {
				enabled = true
			}

			equalizers[sessionId] = equalizer

			val bandLowerRange = equalizer.bandLevelRange.firstOrNull()?.toFloat() ?: -1500f
			val bandUpperRange = equalizer.bandLevelRange.lastOrNull()?.toFloat() ?: 1500f
			val bandCount = equalizer.numberOfBands.toInt()

			// persist the discovered band layout once; every leg reports the
			// same values on a given device, so skip redundant writes
			val config = equalizerManager.config.value
			if (config.bandLowerRange != bandLowerRange ||
				config.bandUpperRange != bandUpperRange ||
				config.bandCount != bandCount
			) {
				scope.launch {
					equalizerManager.setConfig(
						equalizerManager.config.value.copy(
							bandLowerRange = bandLowerRange,
							bandUpperRange = bandUpperRange,
							bandCount = bandCount
						)
					)
				}
			}

			applyConfig(equalizer)
		} catch (ex: Exception) {
			Logger.e(TAG, "error while configuring eq", ex)
		}
	}

	/** Re-applies the current config to every attached equalizer (all legs). */
	fun updateEqualizer() {
		equalizers.values.forEach(::applyConfig)
	}

	private fun applyConfig(equalizer: Equalizer) {
		val config = equalizerManager.config.value
		try {
			// reset all band levels first in case an item in
			// config.bandLevels was removed (e.g. user presses
			// reset in the equalizer settings)
			repeat(equalizer.numberOfBands.toInt()) { band ->
				equalizer.setBandLevel(band.toShort(), 0)
			}
			config.bandLevels.forEach { (band, level) ->
				if (band < equalizer.numberOfBands.toInt()) {
					equalizer.setBandLevel(band.toShort(), level.toInt().toShort())
				}
			}
		} catch (ex: Exception) {
			Logger.e(TAG, "error while setting eq band levels", ex)
		}
	}

	/** Config-flow update: re-apply levels, or rebuild every effect on a mode switch. */
	fun onConfigChanged(config: EqualizerConfig) {
		if (config.mode != equalizerMode) {
			applyEqualizerMode(config.mode)
		} else {
			updateEqualizer()
		}
	}

	fun applyEqualizerMode(mode: EqualizerMode) {
		if (mode == equalizerMode) return
		equalizerMode = mode
		releaseAllEffects()
		legSessions.values
			.filter { it != C.AUDIO_SESSION_ID_UNSET }
			.distinct()
			.forEach(::onSessionStarted)
	}

	// Announces an audio session to the system so external equalizer apps can attach effects to it
	private fun openAudioEffectSession(sessionId: Int) {
		if (sessionId == C.AUDIO_SESSION_ID_UNSET || !openExternalSessions.add(sessionId)) return
		context.sendBroadcast(
			Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
				putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
				putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
				putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
			}
		)
	}

	// Tells external equalizer apps an audio session is going away so they can release their effects
	private fun closeAudioEffectSession(sessionId: Int) {
		if (!openExternalSessions.remove(sessionId)) return
		context.sendBroadcast(
			Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
				putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
				putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
			}
		)
	}

	private fun releaseAllEffects() {
		equalizers.values.forEach { equalizer ->
			runCatching { equalizer.release() }
		}
		equalizers.clear()
		openExternalSessions.toList().forEach(::closeAudioEffectSession)
	}

	/** Releases every effect and detaches from all legs; call from Service.onDestroy. */
	fun release() {
		legListeners.forEach { (player, listener) -> player.removeListener(listener) }
		legListeners.clear()
		legSessions.clear()
		releaseAllEffects()
	}

	private companion object {
		const val TAG = "ExoEqualizerManager"
	}
}

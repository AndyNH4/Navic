/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.exoplayer.impl

import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.cos
import kotlin.math.sin
import paige.navic.domain.manager.PreferenceManager
import paige.navic.util.Logger

/**
 * Dual-ExoPlayer crossfade engine, modelled after react-native-queue-player's
 * production CrossfadeEngine.
 *
 * Two legs rotate roles: the **leading** leg is the one currently audible; the
 * **standby** leg prerolls the next queue item at volume 0 and ramps up while
 * the leading leg ramps down. At fade completion the roles swap. The
 * MediaSession never touches the legs directly — it is bound to a
 * [CrossfadeForwardingPlayer] wrapper for its whole lifetime, so the session
 * is never seeked or re-bound mid-fade (the root cause of the previous
 * single-player implementation cancelling itself).
 *
 * Interruptions (pause / seek / skip / queue edits) reach the legs through the
 * wrapper and are observed here via per-leg listeners; every exit path resets
 * volumes (leading 1, standby 0) and stops + clears the standby leg, falling
 * back to plain playback. When crossfade is disabled the engine is fully
 * inert: no poll, and leg B is not even created until the first armed fade.
 *
 * Everything runs on the main application looper (listeners + Handler), so no
 * extra synchronization is needed.
 */
@UnstableApi
class CrossfadeEngine(
	private val preferenceManager: PreferenceManager,
	private val audioAttributes: AudioAttributes,
	private val legA: ExoPlayer,
	private val standbyLegFactory: () -> ExoPlayer,
	private val wrapper: CrossfadeForwardingPlayer
) {
	private val handler = Handler(legA.applicationLooper)

	/** Created lazily on the first armed fade; null while crossfade never ran. */
	private var legB: ExoPlayer? = null

	/** True while leg A is the leading (currently audible) leg. */
	private var currentRoleIsA = true

	/** True after the standby leg has been prerolled for the upcoming fade. */
	private var armed = false

	/** True while the volume ramp runs (session points at the standby leg). */
	private var fadeActive = false

	/** Guards listener events caused by the engine's own standby-leg writes. */
	private var internalStandbyMutation = false

	/** Leading index whose standby preroll failed; skipped until the track changes. */
	private var armFailedForIndex = C.INDEX_UNSET

	private var triggerIndex = C.INDEX_UNSET
	private var rampDurationMs = 0L
	private var rampStartUptimeMs = 0L
	private var released = false

	/** Per-track diagnostics: which guard blocked arming near the boundary. */
	private var lastDiagIndex = C.INDEX_UNSET

	private val leadingPlayer: ExoPlayer
		get() = if (currentRoleIsA) legA else checkNotNull(legB)

	private val standbyPlayer: ExoPlayer
		get() = if (currentRoleIsA) checkNotNull(legB) else legA

	private fun isLeading(isA: Boolean) = isA == currentRoleIsA

	// --- Leg listeners -------------------------------------------------------

	private fun makeLegListener(isA: Boolean) = object : Player.Listener {

		override fun onIsPlayingChanged(isPlaying: Boolean) {
			if (released) return
			if (!isLeading(isA)) {
				// A pause landing on the standby leg mid-fade is the user
				// pausing through the session (wrapper forwards to it).
				if (!isPlaying) onStandbyTrackCommand()
				return
			}
			if (isPlaying) {
				scheduleArmPoll()
			} else {
				// Pause / focus loss / noisy on the audible leg: stop the fade machinery.
				stopArmPoll()
				if (armed || fadeActive) cancelFade("leading leg paused", promote = fadeActive)
			}
		}

		override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
			if (released) return
			// Any transition is a fresh boundary: a failed preroll may be retried there.
			armFailedForIndex = C.INDEX_UNSET
			if (!isLeading(isA)) {
				onStandbyTrackCommand()
				return
			}
			// Safety net: a natural transition must never inherit a faded volume.
			if (leadingPlayer.volume != 1f && !fadeActive) leadingPlayer.volume = 1f
			when {
				reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && fadeActive -> {
					// The outgoing track ended before the ramp finished
					// (wrong duration metadata or truncation): promote the
					// incoming leg so playback continues without a gap.
					cancelFade("leading leg ended mid-fade", promote = true)
				}
				reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
					// Natural advance while merely armed: the boundary is over.
					cancelFade("leading leg advanced naturally", promote = false)
				}
				armed || fadeActive -> {
					// User skip / queue replace through the wrapper.
					cancelFade("leading leg interrupted", promote = fadeActive)
				}
			}
		}

		override fun onPositionDiscontinuity(
			oldPosition: Player.PositionInfo,
			newPosition: Player.PositionInfo,
			reason: Int
		) {
			if (released) return
			if (!isLeading(isA)) {
				onStandbyTrackCommand()
				return
			}
			if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) return // covered above
			if (armed || fadeActive) cancelFade("user seek on leading leg", promote = fadeActive)
		}

		override fun onTimelineChanged(timeline: Timeline, reason: Int) {
			if (released) return
			if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
			if (!isLeading(isA)) {
				if (!internalStandbyMutation) onStandbyTrackCommand()
				return
			}
			// Queue edited through the wrapper while armed/fading.
			if (armed || fadeActive) cancelFade("queue changed", promote = fadeActive)
		}

		override fun onPlaybackStateChanged(playbackState: Int) {
			if (released) return
			if (isLeading(isA)) {
				if (playbackState == Player.STATE_ENDED && fadeActive) {
					cancelFade("leading leg ended mid-fade", promote = true)
				}
			} else if (!internalStandbyMutation) {
				if (playbackState == Player.STATE_ENDED && (armed || fadeActive)) {
					// The incoming track ended before/while fading in (shorter
					// than the fade): keep the leading leg playing instead.
					cancelFade("standby leg ended early", promote = false)
				}
			}
		}

		override fun onPlayerError(error: PlaybackException) {
			if (released) return
			if (isLeading(isA)) {
				if (fadeActive) cancelFade("leading leg failed mid-fade", promote = true)
			} else if (!internalStandbyMutation && (armed || fadeActive)) {
				// Preroll failed: keep the leading leg untouched and skip this boundary.
				armFailedForIndex = leadingPlayer.currentMediaItemIndex
				Logger.w(TAG, "standby leg failed; boundary skipped", error)
				cancelFade("standby leg error", promote = false)
			}
		}

		/**
		 * A command (pause/seek/skip/queue edit) landed on the standby leg,
		 * which is only possible mid-fade while the wrapper forwards to it.
		 * The command already executed there, so promote that leg and stop
		 * the outgoing one.
		 */
		private fun onStandbyTrackCommand() {
			if (internalStandbyMutation || !fadeActive) return
			cancelFade("user command on incoming leg", promote = true)
		}
	}

	private val legAListener = makeLegListener(isA = true)
	private val legBListener = makeLegListener(isA = false)

	// --- Poll + ramp runnables ----------------------------------------------

	private val armPoll = object : Runnable {
		override fun run() {
			maybeArm()
			scheduleArmPoll()
		}
	}

	private val pendingFadeStart = Runnable {
		if (armed && !fadeActive) startFade()
	}

	private val volumeRamp = object : Runnable {
		override fun run() {
			if (!fadeActive) return
			val t = ((SystemClock.uptimeMillis() - rampStartUptimeMs).toFloat() / rampDurationMs)
				.coerceIn(0f, 1f)
			// Equal-power curve: cos² + sin² = 1 keeps the summed power of the two
			// (uncorrelated) tracks constant; a linear ramp dips ~3dB mid-fade.
			val angle = t * (Math.PI / 2.0).toFloat()
			leadingPlayer.volume = cos(angle)
			standbyPlayer.volume = sin(angle)
			if (t >= 1f) {
				completeFade()
			} else {
				handler.postDelayed(this, RAMP_STEP_MS)
			}
		}
	}

	// --- Lifecycle -----------------------------------------------------------

	fun start() {
		legA.addListener(legAListener)
		scheduleArmPoll()
	}

	fun release() {
		if (released) return
		released = true
		handler.removeCallbacksAndMessages(null)
		cancelFade("engine released", promote = false)
		legA.removeListener(legAListener)
		legB?.removeListener(legBListener)
		legA.release()
		legB?.release()
		legB = null
	}

	/** Called from settings on the main thread when the crossfade preference changes. */
	fun onSettingsChanged() {
		if (preferenceManager.crossfadeEnabled) {
			scheduleArmPoll()
		} else {
			stopArmPoll()
			cancelFade("crossfade disabled", promote = false)
		}
	}

	// --- Arming --------------------------------------------------------------

	private fun scheduleArmPoll() {
		handler.removeCallbacks(armPoll)
		if (released || !preferenceManager.crossfadeEnabled) return
		if (armed || fadeActive || !leadingPlayer.isPlaying) return
		handler.postDelayed(armPoll, POLL_INTERVAL_MS)
	}

	private fun stopArmPoll() {
		handler.removeCallbacks(armPoll)
	}

	private fun maybeArm() {
		if (released || armed || fadeActive) return
		if (!preferenceManager.crossfadeEnabled) return
		val leading = leadingPlayer
		val duration = leading.duration
		val fadeMs = (preferenceManager.crossfadeDurationSeconds * 1000f).toLong()
			.coerceIn(MIN_FADE_MS, MAX_FADE_MS)
		// Near a boundary without arming: log once per track which guard blocked it.
		if (duration > 0 && duration - leading.currentPosition < fadeMs + 15_000L &&
			leading.currentMediaItemIndex != lastDiagIndex
		) {
			lastDiagIndex = leading.currentMediaItemIndex
			Logger.i(
				TAG,
				"arm check: idx=${leading.currentMediaItemIndex} playing=${leading.isPlaying} " +
					"state=${leading.playbackState} repeat=${leading.repeatMode} " +
					"next=${leading.nextMediaItemIndex} failedIdx=$armFailedForIndex " +
					"duration=${duration}ms pos=${leading.currentPosition}ms " +
					"threshold=${duration - fadeMs - PREROLL_LEAD_MS}ms"
			)
		}
		if (!leading.isPlaying || leading.playbackState != Player.STATE_READY) return
		// repeat-one replays the same track natively; never fade past it
		if (leading.repeatMode == Player.REPEAT_MODE_ONE) return
		val currentIndex = leading.currentMediaItemIndex
		if (currentIndex == C.INDEX_UNSET || currentIndex == armFailedForIndex) return
		val nextIndex = leading.nextMediaItemIndex
		if (nextIndex == C.INDEX_UNSET || nextIndex == currentIndex) return // queue end (no wrap)
		if (duration <= 0) return // unknown duration (e.g. live streams)
		if (duration <= fadeMs * 2) return // track too short for a meaningful crossfade
		val armThreshold = duration - fadeMs - PREROLL_LEAD_MS
		if (leading.currentPosition < armThreshold) return
		arm(fadeMs, nextIndex)
	}

	private fun arm(fadeMs: Long, nextIndex: Int) {
		val leading = leadingPlayer
		val standby = getOrCreateStandbyLeg() ?: return
		armed = true
		triggerIndex = leading.currentMediaItemIndex
		rampDurationMs = fadeMs
		internalStandbyMutation = true
		try {
			// Load the full queue so the standby's indices match the leading
			// leg's global index space (session reads stay meaningful).
			val count = leading.mediaItemCount
			val items = ArrayList<MediaItem>(count)
			for (i in 0 until count) items.add(leading.getMediaItemAt(i))
			standby.setMediaItems(items, nextIndex, /* startPositionMs = */ 0L)
			standby.repeatMode = leading.repeatMode
			standby.shuffleModeEnabled = leading.shuffleModeEnabled
			standby.volume = 0f
			standby.prepare()
			standby.play()
		} catch (e: Exception) {
			// never let arming break the leading leg's playback
			Logger.w(TAG, "arming failed; boundary skipped", e)
			armFailedForIndex = triggerIndex
			armed = false
			runCatching {
				standby.stop()
				standby.clearMediaItems()
				standby.volume = 0f
			}
			return
		} finally {
			internalStandbyMutation = false
		}
		// A listener may have cancelled mid-arm (the cancel path clears the
		// standby leg); do not re-arm or schedule a fade on top of it.
		if (!armed) return
		Logger.i(TAG, "armed: nextIndex=$nextIndex fadeMs=$fadeMs")
		// Small settle so the standby's audio path is hot when the ramp starts.
		handler.postDelayed(pendingFadeStart, PREROLL_SETTLE_MS)
	}

	private fun getOrCreateStandbyLeg(): ExoPlayer? {
		// After the first role swap the standby leg is leg A: always resolve the
		// standby by role, never by "leg B exists" — otherwise arming rewrites the
		// currently audible leg's queue mid-playback (hard cut + stuck mute).
		if (!currentRoleIsA) return legA
		legB?.let { return it }
		return runCatching {
			standbyLegFactory().apply {
				addListener(legBListener)
				volume = 0f
			}
		}.onFailure {
			Logger.w(TAG, "standby leg creation failed", it)
			armFailedForIndex = leadingPlayer.currentMediaItemIndex
		}.getOrNull()?.also { legB = it }
	}

	// --- Fade ----------------------------------------------------------------

	private fun startFade() {
		val leading = leadingPlayer
		val standby = standbyPlayer
		if (!leading.isPlaying || leading.currentMediaItemIndex != triggerIndex) {
			cancelFade("leading leg moved on before fade start", promote = false)
			return
		}
		when (standby.playbackState) {
			Player.STATE_READY -> Unit
			Player.STATE_BUFFERING -> {
				// Slow preroll: wait a little longer; every exit path disarms,
				// which stops this reschedule loop.
				handler.postDelayed(pendingFadeStart, PREROLL_SETTLE_MS)
				return
			}
			else -> {
				cancelFade("standby leg not playable", promote = false)
				return
			}
		}
		// Clamp the ramp to the time actually left on the outgoing track.
		val remaining = leading.duration - leading.currentPosition
		if (remaining < MIN_FADE_MS) {
			cancelFade("too late to fade", promote = false)
			return
		}
		rampDurationMs = rampDurationMs.coerceAtMost(remaining)
		rampStartUptimeMs = SystemClock.uptimeMillis()
		fadeActive = true
		// Flip the session (metadata + commands) to the incoming leg from 00:00.
		wrapper.onCrossfadeBegin(standby)
		handler.post(volumeRamp)
		Logger.i(TAG, "fade started: durationMs=$rampDurationMs")
	}

	private fun completeFade() {
		Logger.i(TAG, "fade complete; swapping roles")
		val oldLeading = leadingPlayer
		val newLeading = standbyPlayer
		currentRoleIsA = !currentRoleIsA
		fadeActive = false
		armed = false
		newLeading.volume = 1f
		// The outgoing leg becomes the idle standby.
		oldLeading.volume = 0f
		oldLeading.stop()
		oldLeading.clearMediaItems()
		handOverAudioFocus(newLeading, oldLeading)
		// Already bound to this leg since fade start; re-affirm + refresh state.
		wrapper.onRoleSwapped(newLeading)
		scheduleArmPoll()
	}

	/**
	 * Every fade exit other than completion. With [promote] the user's command
	 * already ran on the standby (incoming) leg, so that leg keeps its state and
	 * becomes leading; otherwise the leading leg keeps playing and the wrapper
	 * is reverted to it if the fade had already begun.
	 */
	private fun cancelFade(reason: String, promote: Boolean) {
		if (!armed && !fadeActive) return
		Logger.i(TAG, "fade cancelled: $reason (promote=$promote)")
		handler.removeCallbacks(pendingFadeStart)
		handler.removeCallbacks(volumeRamp)
		val wasFadeActive = fadeActive
		fadeActive = false
		armed = false
		val oldLeading = leadingPlayer
		val oldStandby = standbyPlayer
		if (promote && wasFadeActive) {
			currentRoleIsA = !currentRoleIsA
			oldStandby.volume = 1f
			handOverAudioFocus(newLeading = oldStandby, oldLeading = oldLeading)
			// The wrapper already points at the promoted leg; re-affirm state.
			wrapper.onRoleSwapped(oldStandby)
			oldLeading.stop()
			oldLeading.clearMediaItems()
			oldLeading.volume = 0f
		} else {
			oldLeading.volume = 1f
			if (wasFadeActive) wrapper.onCrossfadeCancelled(oldLeading)
			oldStandby.stop()
			oldStandby.clearMediaItems()
			oldStandby.volume = 0f
		}
		scheduleArmPoll()
	}

	/**
	 * Exactly one leg owns audio focus at any time (two Media3 focus managers
	 * would fight each other). Hand focus handling to the leg that becomes
	 * audible; requesting on the new leg first keeps focus inside the app.
	 */
	private fun handOverAudioFocus(newLeading: ExoPlayer, oldLeading: ExoPlayer) {
		runCatching { newLeading.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true) }
		runCatching { oldLeading.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false) }
	}

	private companion object {
		const val TAG = "Crossfade"
		const val POLL_INTERVAL_MS = 250L
		const val RAMP_STEP_MS = 50L
		const val MIN_FADE_MS = 1_000L
		const val MAX_FADE_MS = 12_000L
		// lead time before the fade window to preroll the standby leg
		const val PREROLL_LEAD_MS = 500L
		// settle delay between standby.play() and the ramp start
		const val PREROLL_SETTLE_MS = 150L
	}
}

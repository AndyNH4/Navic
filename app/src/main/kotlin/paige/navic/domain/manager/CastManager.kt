/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import android.content.Context
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.BackgroundExecutor
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import paige.navic.R
import paige.navic.exoplayer.ExoStateHolder
import paige.navic.util.Logger

/**
 * Moves playback between the local ExoPlayer and a Cast receiver.
 *
 * A single [RemoteCastPlayer] is kept for the app's lifetime; it tracks Cast
 * sessions by itself and reports them through [SessionAvailabilityListener].
 * When a session starts, the local queue, position and modes are copied over
 * and the MediaSession is re-pointed at the remote player via
 * MediaSession.setPlayer(); when the session ends, the state is copied back.
 * The local player is only paused while casting and never released. Any
 * failure during a transfer falls back to local playback.
 *
 * [initialize] must run on the main thread of the main process and never
 * throws: on devices without Google Play services the CastContext load fails
 * asynchronously, nothing crashes and the Cast entry point stays hidden.
 */
@UnstableApi
class CastManager(
	private val context: Context,
	private val stateHolder: ExoStateHolder,
	private val snackBarManager: SnackBarManager
) {
	private val _isCasting = MutableStateFlow(false)
	val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

	/** True once the Cast framework is up; drives the Cast button visibility. */
	private val _isCastAvailable = MutableStateFlow(false)
	val isCastAvailable: StateFlow<Boolean> = _isCastAvailable.asStateFlow()

	private var castPlayer: RemoteCastPlayer? = null

	// Keeps the notification's shuffle/repeat buttons in sync while casting.
	private val castPlayerListener = object : Player.Listener {
		override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
			stateHolder.refreshSessionButtons()
		}

		override fun onRepeatModeChanged(repeatMode: Int) {
			stateHolder.refreshSessionButtons()
		}
	}

	/** Starts asynchronous Cast initialization; safe to call on any device. */
	fun initialize() {
		try {
			// Uses the manifest-registered NavicCastOptionsProvider. The async
			// load is shared with the media3 Cast singleton, which the
			// RemoteCastPlayer below attaches to on creation.
			CastContext.getSharedInstance(context, BackgroundExecutor.get())
				.addOnSuccessListener { _isCastAvailable.value = true }
				.addOnFailureListener {
					Logger.w(TAG, "CastContext load failed, cast is unavailable", it)
				}
			val player = RemoteCastPlayer.Builder(context).build()
			player.addListener(castPlayerListener)
			player.setSessionAvailabilityListener(object : SessionAvailabilityListener {
				override fun onCastSessionAvailable() = transferToCast(player)
				override fun onCastSessionUnavailable() = transferToLocal(player)
			})
			castPlayer = player
		} catch (e: Exception) {
			// e.g. not the main process: stay local-only
			Logger.w(TAG, "Cast is unavailable on this device", e)
		}
	}

	/**
	 * Called by ExoPlaybackService once the player and session exist, so a Cast
	 * session started before playback (e.g. from the Now Playing screen with an
	 * empty queue) still takes over.
	 */
	fun onPlaybackServiceReady() {
		val player = castPlayer ?: return
		if (player.isCastSessionAvailable) {
			transferToCast(player)
		}
	}

	private fun transferToCast(player: RemoteCastPlayer) {
		if (!stateHolder.isReady || _isCasting.value) return
		val session = stateHolder.mediaSession
		val local = stateHolder.playerInstance
		if (session.player === player) return
		try {
			val items = List(local.mediaItemCount) { local.getMediaItemAt(it) }
			val index = local.currentMediaItemIndex
			val position = local.currentPosition.coerceAtLeast(0L)
			val playWhenReady =
				local.playWhenReady && local.playbackState != Player.STATE_ENDED
			local.pause()
			if (items.isNotEmpty()) {
				// RemoteCastPlayer does not support shuffle mode; the queue is
				// transferred in its current (possibly shuffled) order.
				player.repeatMode = local.repeatMode
				// read by the queue load as the autoplay flag
				player.playWhenReady = playWhenReady
				player.setMediaItems(items, index.coerceAtLeast(0), position)
				// best effort: the receiver may drop a rate set before the load
				player.playbackParameters = local.playbackParameters
			}
			session.setPlayer(player)
			stateHolder.refreshSessionButtons()
			_isCasting.value = true
			snackBarManager.notify(R.string.notice_cast_connected)
			Logger.i(TAG, "transferred playback to the cast device")
		} catch (e: Exception) {
			Logger.w(TAG, "transfer to cast failed, staying local", e)
			runCatching {
				if (session.player !== local) session.setPlayer(local)
			}
			_isCasting.value = false
		}
	}

	private fun transferToLocal(player: RemoteCastPlayer) {
		if (!stateHolder.isReady) return
		val session = stateHolder.mediaSession
		if (session.player !== player) {
			// never transferred (e.g. the session ended before playback started)
			_isCasting.value = false
			return
		}
		try {
			val local = stateHolder.playerInstance
			val items = List(player.mediaItemCount) { player.getMediaItemAt(it) }
			val index = player.currentMediaItemIndex
			val position = player.currentPosition.coerceAtLeast(0L)
			val resume = player.playWhenReady && player.playbackState != Player.STATE_ENDED
			session.setPlayer(local)
			if (items.isNotEmpty() && index != C.INDEX_UNSET) {
				local.setMediaItems(items, index, position)
				local.repeatMode = player.repeatMode
				local.prepare()
				if (resume) local.play()
			}
			stateHolder.refreshSessionButtons()
			Logger.i(TAG, "transferred playback back to the local player")
		} catch (e: Exception) {
			Logger.w(TAG, "transfer back to the local player failed", e)
			runCatching {
				if (session.player !== stateHolder.playerInstance) {
					session.setPlayer(stateHolder.playerInstance)
				}
			}
		} finally {
			_isCasting.value = false
			snackBarManager.notify(R.string.notice_cast_disconnected)
		}
	}

	private companion object {
		const val TAG = "CastManager"
	}
}

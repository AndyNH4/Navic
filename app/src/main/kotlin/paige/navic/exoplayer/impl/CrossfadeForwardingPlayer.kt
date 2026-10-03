/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.exoplayer.impl

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * MediaSession-stable wrapper around the [CrossfadeEngine]'s two underlying
 * ExoPlayer legs.
 *
 * MediaSession binds to a single Player for its lifetime, but inside the
 * engine the leading / standby legs swap roles at every fade. The session
 * binds to this wrapper forever and the wrapper re-targets the delegate at
 * fade begin / role swap / fade cancel. All controller commands (play, pause,
 * seeks, skips, queue edits, shuffle/repeat) are forwarded to the currently
 * wrapped leg; the engine observes the effects through its leg listeners.
 */
@UnstableApi
class CrossfadeForwardingPlayer(leading: Player) : ForwardingSimpleBasePlayer(leading) {

	/** A fade started ramping: point the session at the incoming leg. */
	fun onCrossfadeBegin(standby: Player) {
		setPlayer(standby)
		invalidateState()
	}

	/** A fade completed: point the session at the new leading leg. */
	fun onRoleSwapped(newLeading: Player) {
		setPlayer(newLeading)
		invalidateState()
	}

	/** An in-flight fade was cancelled: revert the session to the leading leg. */
	fun onCrossfadeCancelled(leading: Player) {
		setPlayer(leading)
		invalidateState()
	}
}

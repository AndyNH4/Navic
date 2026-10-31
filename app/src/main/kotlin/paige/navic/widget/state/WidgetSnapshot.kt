/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.state

import androidx.media3.common.Player
import kotlinx.serialization.Serializable

@Serializable
data class WidgetSnapshot(
	val revision: Long = 0,
	val mediaId: String? = null,
	val title: String? = null,
	val artist: String? = null,
	val artworkUri: String? = null,
	val isPlaying: Boolean = false,
	val playWhenReady: Boolean = false,
	val isBuffering: Boolean = false,
	val isShuffleEnabled: Boolean = false,
	val repeatMode: Int = Player.REPEAT_MODE_OFF,
	val currentIndex: Int = -1,
	val positionMs: Long = 0,
	val durationMs: Long = 0,
	val canPlay: Boolean = true,
	val canPrevious: Boolean = false,
	val canNext: Boolean = false,
	val canShuffle: Boolean = false,
	val canRepeat: Boolean = false,
	val canSeek: Boolean = false,
	val canStar: Boolean = false,
	val isStarred: Boolean = false,
	val pendingCommand: String? = null,
	val queue: List<WidgetQueueItem> = emptyList()
) {
	val hasContent: Boolean
		get() = mediaId != null
}

@Serializable
data class WidgetQueueItem(
	val mediaId: String,
	val index: Int,
	val title: String,
	val artist: String? = null,
	val artworkUri: String? = null,
	val isCurrent: Boolean = false
)

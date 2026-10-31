/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.state

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.SongDao
import paige.navic.exoplayer.RADIO_ID_PREFIX
import paige.navic.util.Logger
import kotlin.math.min

class WidgetStatePublisher(
	private val context: Context,
	private val store: WidgetSnapshotStore,
	private val updateDispatcher: WidgetUpdateDispatcher,
	private val artworkLoader: WidgetArtworkLoader,
	private val songDao: SongDao
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private var activePlayer: Player? = null
	private var publishJob: Job? = null
	private var starredJob: Job? = null
	private var artworkJob: Job? = null

	private val listener = object : Player.Listener {
		override fun onEvents(player: Player, events: Player.Events) {
			publish()
		}
	}

	fun start(player: Player) {
		onSessionPlayerChanged(player)
	}

	fun onSessionPlayerChanged(player: Player) {
		if (activePlayer === player) {
			publish()
			return
		}
		activePlayer?.removeListener(listener)
		activePlayer = player
		player.addListener(listener)
		publish()
	}

	fun stop() {
		activePlayer?.removeListener(listener)
		activePlayer = null
		publishJob?.cancel()
		starredJob?.cancel()
		artworkJob?.cancel()
		scope.launch {
			val changed = store.update { current ->
				current.copy(
					isPlaying = false,
					playWhenReady = false,
					isBuffering = false,
					pendingCommand = null,
					canPlay = true,
					canPrevious = false,
					canNext = false,
					canSeek = false
				)
			}
			if (changed != null) updateDispatcher.updateNowPlaying()
		}
	}

	fun refreshStarred() {
		val mediaId = activePlayer?.currentMediaItem?.mediaId ?: return
		loadStarred(mediaId)
	}

	private fun publish() {
		val player = activePlayer ?: return
		publishJob?.cancel()
		publishJob = scope.launch {
			try {
				val startedAt = SystemClock.elapsedRealtime()
				val next = player.toSnapshot()
				val previous = store.current()
				val changed = store.update { current ->
					next.copy(
						isStarred = if (current.mediaId == next.mediaId) current.isStarred else false,
						pendingCommand = null
					)
				}
				if (changed != null) updateDispatcher.updateNowPlaying()
				if (previous.mediaId != next.mediaId) {
					Logger.i(TAG, "widget transition published in ${SystemClock.elapsedRealtime() - startedAt}ms")
					next.mediaId?.let(::loadStarred)
				}
				if (previous.artworkUri != next.artworkUri) {
					artworkJob?.cancel()
					artworkJob = scope.launch {
						runCatching { artworkLoader.prefetch(next.artworkUri) }
							.onFailure { Logger.w(TAG, "failed to prefetch widget artwork", it) }
					}
				}
			} catch (error: Exception) {
				Logger.w(TAG, "failed to publish widget state", error)
			}
		}
	}

	private fun loadStarred(mediaId: String) {
		starredJob?.cancel()
		if (mediaId.startsWith(RADIO_ID_PREFIX)) return
		starredJob = scope.launch {
			val isStarred = runCatching { songDao.isSongStarred(mediaId) }.getOrDefault(false)
			val changed = store.update { current ->
				if (current.mediaId == mediaId) current.copy(isStarred = isStarred) else current
			}
			if (changed != null) updateDispatcher.updateNowPlaying()
		}
	}

	private fun Player.toSnapshot(): WidgetSnapshot {
		val item = currentMediaItem
		val metadata = item?.mediaMetadata
		val mediaId = item?.mediaId?.takeIf { it.isNotBlank() }
		val index = currentMediaItemIndex
		val queue = if (item != null && index in 0 until mediaItemCount) {
			(index until min(mediaItemCount, index + QUEUE_PREVIEW_SIZE)).map { itemIndex ->
				val queueItem = getMediaItemAt(itemIndex)
				val queueMetadata = queueItem.mediaMetadata
				WidgetQueueItem(
					mediaId = queueItem.mediaId,
					index = itemIndex,
					title = queueMetadata.title?.toString().orEmpty(),
					artist = queueMetadata.artist?.toString()
						?: queueMetadata.subtitle?.toString(),
					artworkUri = queueMetadata.artworkUri?.toString(),
					isCurrent = itemIndex == index
				)
			}
		} else {
			emptyList()
		}
		val canSeek = isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
			|| isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
			|| isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
		return WidgetSnapshot(
			mediaId = mediaId,
			title = metadata?.title?.toString(),
			artist = metadata?.artist?.toString()?.ifBlank { null }
				?: metadata?.subtitle?.toString()?.ifBlank { null },
			artworkUri = metadata?.artworkUri?.toString(),
			isPlaying = isPlaying,
			playWhenReady = playWhenReady,
			isBuffering = playbackState == Player.STATE_BUFFERING,
			isShuffleEnabled = shuffleModeEnabled,
			repeatMode = repeatMode,
			currentIndex = index,
			positionMs = currentPosition.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
			durationMs = duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
			canPlay = isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
			canPrevious = item != null && canSeek,
			canNext = hasNextMediaItem()
				&& isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
			canShuffle = isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE),
			canRepeat = isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE),
			canSeek = canSeek,
			canStar = mediaId != null && !mediaId.startsWith(RADIO_ID_PREFIX),
			queue = queue
		)
	}

	private companion object {
		const val TAG = "WidgetStatePublisher"
		const val QUEUE_PREVIEW_SIZE = 6
	}
}

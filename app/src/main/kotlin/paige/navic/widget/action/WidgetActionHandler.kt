/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.action

import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.domain.repository.PlayerStateRepository
import paige.navic.domain.repository.SongRepository
import paige.navic.exoplayer.ExoStateHolder
import paige.navic.util.Logger
import paige.navic.widget.state.WidgetSnapshotStore
import paige.navic.widget.state.WidgetStatePublisher
import paige.navic.widget.state.WidgetUpdateDispatcher

class WidgetActionHandler(
	private val controller: WidgetController,
	private val store: WidgetSnapshotStore,
	private val updateDispatcher: WidgetUpdateDispatcher,
	private val playerStateRepository: PlayerStateRepository,
	private val songDao: SongDao,
	private val songRepository: SongRepository,
	private val statePublisher: WidgetStatePublisher
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private var timeoutJob: Job? = null

	suspend fun execute(command: String) = runPending(command) {
		if (command == WidgetCommands.TOGGLE_STAR) {
			toggleStar()
		} else {
			controller.withController { player ->
				when (command) {
					WidgetCommands.PLAY_PAUSE -> {
						if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
							player.pause()
						} else {
							player.play()
						}
					}

					WidgetCommands.PREVIOUS -> {
						if (player.hasPreviousMediaItem() && player.currentPosition <= 1000) {
							player.seekToPreviousMediaItem()
						} else {
							player.seekTo(0)
						}
					}

					WidgetCommands.NEXT -> {
						if (player.hasNextMediaItem()) player.seekToNextMediaItem()
					}

					WidgetCommands.SHUFFLE -> sendSessionCommand(
						player,
						ExoStateHolder.COMMAND_SHUFFLE
					)

					WidgetCommands.REPEAT -> sendSessionCommand(
						player,
						ExoStateHolder.COMMAND_REPEAT
					)

					else -> throw IllegalArgumentException("Unknown widget command: $command")
				}
			}
		}
	}

	suspend fun executeQueueItem(mediaId: String, index: Int, expectedRevision: Long) {
		if (store.current().revision != expectedRevision) {
			updateDispatcher.updateNowPlaying()
			return
		}
		runPending("${WidgetCommands.QUEUE_ITEM}:$mediaId") {
			controller.withController { player ->
				if (!player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) {
					throw IllegalStateException("Queue seeking is unavailable")
				}
				val targetIndex = if (index in 0 until player.mediaItemCount
					&& player.getMediaItemAt(index).mediaId == mediaId
				) {
					index
				} else {
					(0 until player.mediaItemCount).firstOrNull {
						player.getMediaItemAt(it).mediaId == mediaId
					}
				} ?: throw IllegalStateException("Queue item is no longer available")
				player.seekTo(targetIndex, 0L)
				player.play()
			}
			val saved = playerStateRepository.state.first()
			val savedIndex = saved.queue.indexOfFirst { it.id == mediaId }
			if (savedIndex >= 0) {
				playerStateRepository.setState(
					saved.copy(
						currentIndex = savedIndex,
						currentSong = saved.queue[savedIndex],
						isPaused = false,
						progress = 0f,
						isLoading = true
					)
				)
			}
		}
	}

	private suspend fun runPending(command: String, block: suspend () -> Unit) {
		val startedAt = SystemClock.elapsedRealtime()
		store.setPending(command)
		scope.launch(Dispatchers.Default) { updateDispatcher.updateNowPlaying() }
		try {
			val completed = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { block() }
			if (completed == null) throw IllegalStateException("Widget command timed out")
			Logger.i(TAG, "widget command sent in ${SystemClock.elapsedRealtime() - startedAt}ms: $command")
			timeoutJob?.cancel()
			timeoutJob = scope.launch {
				delay(PENDING_TIMEOUT_MS)
				store.clearPending(command)
				updateDispatcher.updateNowPlaying()
			}
		} catch (error: Exception) {
			store.clearPending(command)
			updateDispatcher.updateNowPlaying()
			Logger.w(TAG, "widget command failed: $command", error)
		}
	}

	private suspend fun toggleStar() {
		val snapshot = store.current()
		val mediaId = snapshot.mediaId ?: throw IllegalStateException("No current song")
		val savedSong = playerStateRepository.state.first().queue.firstOrNull { it.id == mediaId }
		val song = savedSong ?: songDao.getSongById(mediaId)?.toDomainModel()
			?: throw IllegalStateException("Current song is not available locally")
		val isStarred = songDao.isSongStarred(mediaId)
		if (isStarred) songRepository.unstarSong(song) else songRepository.starSong(song)
		store.update { current ->
			if (current.mediaId == mediaId) {
				current.copy(isStarred = !isStarred, pendingCommand = null)
			} else {
				current
			}
		}
		updateDispatcher.updateNowPlaying()
		statePublisher.refreshStarred()
	}

	private suspend fun sendSessionCommand(player: MediaController, action: String) {
		val result = player.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), Bundle.EMPTY).await()
		if (result.resultCode != SessionResult.RESULT_SUCCESS) {
			throw IllegalStateException("Session command failed: $action")
		}
	}

	private companion object {
		const val TAG = "WidgetActionHandler"
		const val COMMAND_TIMEOUT_MS = 8_000L
		const val PENDING_TIMEOUT_MS = 5_000L
	}
}

object WidgetCommands {
	const val PLAY_PAUSE = "play_pause"
	const val PREVIOUS = "previous"
	const val NEXT = "next"
	const val SHUFFLE = "shuffle"
	const val REPEAT = "repeat"
	const val TOGGLE_STAR = "toggle_star"
	const val QUEUE_ITEM = "queue_item"
}

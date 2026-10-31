/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.shared

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.domain.manager.AudioGainManager
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainRadio
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.DomainSongCollection
import paige.navic.domain.model.settings.ReplayGainMode
import paige.navic.domain.repository.PlayerStateRepository
import paige.navic.domain.repository.SongRepository
import paige.navic.exoplayer.ExoStateHolder
import paige.navic.exoplayer.PlaybackMediaItemFactory
import paige.navic.exoplayer.PlaybackQueueResolver
import paige.navic.exoplayer.toPlaybackSong
import paige.navic.ui.core.PlayerUiState
import paige.navic.util.Logger
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
class AndroidMediaPlayerViewModel(
	stateRepository: PlayerStateRepository,
	songRepository: SongRepository,
	downloadManager: DownloadManager,
	connectivityManager: ConnectivityManager,
	preferenceManager: PreferenceManager,
	private val audioGainManager: AudioGainManager,
	private val mediaItemFactory: PlaybackMediaItemFactory,
	private val queueResolver: PlaybackQueueResolver,
	private val application: Application,
	private val albumDao: AlbumDao,
	private val snackBarManager: SnackBarManager
) : MediaPlayerViewModel(
	stateRepository = stateRepository,
	songRepository = songRepository,
	connectivityManager = connectivityManager,
	downloadManager = downloadManager,
	preferenceManager = preferenceManager
) {
	private var controller: MediaController? = null
	private var controllerFuture: ListenableFuture<MediaController>? = null

	private var loadingCollectionId: String? = null

	private var pendingSyncState: PlayerUiState? = null
	private var queueSyncGeneration = 0L

	init {
		connectToService()
	}

	// just because I hate writing boilerplate every single line
	private fun launchInView(immediate: Boolean = false, block: suspend CoroutineScope.() -> Unit) {
		val context = if (immediate) {
			Dispatchers.Main.immediate
		} else {
            EmptyCoroutineContext
		}

		viewModelScope.launch(
			context = context,
			block = block
		)
	}

	private fun connectToService() = launchInView {
		val sessionToken = ExoStateHolder.newSessionToken(application)
		controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
		controllerFuture?.addListener({
			controller = controllerFuture?.get()
			setupController()
		}, MoreExecutors.directExecutor())
	}

	private fun setupController() = launchInView {
		controller?.apply {
			addListener(object : Player.Listener {
				override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
					handleControllerTimelineChanged {
						skipUnavailableSong()
						checkAndAutoFillQueue()
					}
				}

				override fun onIsPlayingChanged(isPlaying: Boolean) {
					if (isPlaying) startProgressLoop()
					updatePlaybackState()
				}

				override fun onPlaybackStateChanged(playbackState: Int) {
					_uiState.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
					updatePlaybackState()
				}

				override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
					_uiState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
				}

				override fun onRepeatModeChanged(repeatMode: Int) {
					_uiState.update { it.copy(repeatMode = repeatMode) }
				}

				override fun onTracksChanged(tracks: Tracks) {
					updatePlaybackProperties(tracks)
				}

				override fun onTimelineChanged(timeline: Timeline, reason: Int) {
					handleControllerTimelineChanged()
				}
			})
			synchronizeQueueFromController()
			updatePlaybackState()
			updatePlaybackProperties(currentTracks)

			downloadManager.allDownloads.first()
			pendingSyncState?.let { state ->
				syncPlayerWithState(state)
				pendingSyncState = null
			}
			checkAndAutoFillQueue()
		}
	}

	private fun handleControllerTimelineChanged(after: () -> Unit = {}) {
		viewModelScope.launch {
			synchronizeQueueFromController()
			updatePlaybackState()
			after()
		}
	}

	private suspend fun synchronizeQueueFromController() {
		val player = controller ?: return
		val items = List(player.mediaItemCount) { player.getMediaItemAt(it) }
		if (items.map { it.mediaId } == _uiState.value.queue.map { it.id }) return
		val generation = ++queueSyncGeneration
		val resolved = queueResolver.resolve(items, _uiState.value.queue)
		if (generation != queueSyncGeneration || player !== controller) return
		if (resolved == null) {
			Logger.w("MediaPlayer", "could not resolve the controller queue")
			return
		}
		_uiState.update { it.copy(queue = resolved) }
	}

	/**
	 * strategically skip around in the queue until the
	 * current song is available while avoiding infinite
	 * loops
	 *
	 * this **INTENTIONALLY** does not check for if the song
	 * is not downloaded and if the device is offline
	 *
	 * this used to check for that but because there have
	 * been cases where the device is falsely identified
	 * as being offline that's no longer the case, so we
	 * just try to play the song anyway
	 */
	private fun skipUnavailableSong() {
		val currentSong = _uiState.value.currentSong ?: return
		if (!isExplicit(currentSong)) return
		Logger.i("MediaPlayer", "trying to skip unavailable song")
		val queue = _uiState.value.queue
		val currentIdx = queue.indexOf(currentSong)

		// look for the next available song, wrapping around, but stop before
		// we loop back past our own starting point
		val nextAvailableIdx = (1..queue.size)
			.map { offset -> (currentIdx + offset) % queue.size }
			.firstOrNull { index -> !isExplicit(queue[index]) }

		if (nextAvailableIdx == null) {
			Logger.i(
				"MediaPlayer",
				"pausing because this song is unavailable and there isn't anything to skip to"
			)
			controller?.pause()
			return
		}

		if (nextAvailableIdx <= currentIdx) {
			Logger.i(
				"MediaPlayer",
				"skipping and pausing because the last song in the queue was unavailable"
			)
			controller?.seekTo(nextAvailableIdx, 0L)
			controller?.pause()
		} else {
			// just skip to the next song
			controller?.seekTo(nextAvailableIdx, 0L)
		}
	}

	private fun refreshCurrentCollection(albumId: String) {
		if (loadingCollectionId == albumId) return
		loadingCollectionId = albumId

		viewModelScope.launch {
			runCatching {
				val album = albumDao.getAlbumById(albumId)

				_uiState.update { it.copy(currentCollection = album?.toDomainModel()) }
			}.onFailure {
				loadingCollectionId = null
			}
		}
	}

	private fun updatePlaybackState() {
		val controller = controller ?: return
		val index = controller.currentMediaItemIndex
		if (index == C.INDEX_UNSET) return

		val currentSong = _uiState.value.queue.getOrNull(index)

		val derivedCollection = currentSong?.let { song ->
			val stateCollection = _uiState.value.currentCollection

			if (stateCollection?.id == song.albumId.toString()) {
				stateCollection
			} else {
				refreshCurrentCollection(song.albumId.toString())
				null
			}
		}

		_uiState.update { state ->
			state.copy(
				currentIndex = index,
				currentSong = currentSong,
				currentCollection = derivedCollection ?: state.currentCollection,
				isPaused = (!controller.playWhenReady || controller.playbackState == Player.STATE_ENDED),
				isShuffleEnabled = controller.shuffleModeEnabled,
				repeatMode = controller.repeatMode
			)
		}
		applyAudioGain()
		updateProgress()
	}

	private fun applyAudioGain() = audioGainManager.apply {
		setAmplifierValues(preferenceManager.rgAmpGain, preferenceManager.ampGain)

		if (preferenceManager.replayGainMode != ReplayGainMode.Off) {
			val currentSong = _uiState.value.currentSong
			val replayGain = currentSong?.replayGain

			if (replayGain != null) {
				setReplayGainMetadata(replayGain)

				if (preferenceManager.replayGainMode != ReplayGainMode.Dynamic) {
					applyGainMode(preferenceManager.replayGainMode)
				} else {
					if (_uiState.value.queue.all { it.albumId == currentSong.albumId }) {
						applyGainMode(ReplayGainMode.Album)
					} else {
						applyGainMode(ReplayGainMode.Track)
					}
				}

				return@apply
			}

			setReplayGainMetadata(null)
			return@apply
		}
		resetGain()
	}

	override fun syncPlayerWithState(state: PlayerUiState) = launchInView {
		val player = controller

		if (player == null) {
			pendingSyncState = state
			return@launchInView
		}

		if (state.queue.isEmpty() || player.mediaItemCount > 0) {
			synchronizeQueueFromController()
			updatePlaybackState()
			return@launchInView
		}

		val mediaItems = withContext(Dispatchers.Default) {
            state.queue.map { it.toMediaItem() }
        }

		player.apply {
			setMediaItems(mediaItems)

			shuffleModeEnabled = state.isShuffleEnabled
			repeatMode = state.repeatMode
			playbackParameters = PlaybackParameters(state.playbackSpeed)
		}

		val index = if (state.currentIndex in mediaItems.indices) state.currentIndex else 0

		val songDurationMs = state.queue.getOrNull(index)?.duration?.inWholeMilliseconds ?: 0L

		val position = if (songDurationMs > 0) {
			(state.progress * songDurationMs).toLong()
		} else {
			0L
		}

		player.seekTo(index, position)
		player.prepare()
		if (!state.isPaused) {
			player.play()
		}
	}

	private fun startProgressLoop() = launchInView {
		while (controller?.isPlaying == true) {
			val player = controller ?: break
			val duration = player.duration
			if (duration > 0) {
				val progress =
					(player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
				_uiState.update { it.copy(progress = progress) }
			}
            delay(200.milliseconds)
		}
	}

	private fun updateProgress() = controller?.also { player ->
		val duration = player.duration
		if (duration > 0) {
			val pos = player.currentPosition
			val progress = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
			_uiState.update { it.copy(progress = progress) }
		}
	}

	@OptIn(UnstableApi::class)
	private fun updatePlaybackProperties(tracks: Tracks) {
		val audioGroup =
			tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
		if (audioGroup != null) {
			for (i in 0 until audioGroup.length) {
				if (audioGroup.isTrackSelected(i)) {
					val format = audioGroup.getTrackFormat(i)
					Logger.i("MediaPlayer", "active track format: $format")
					_uiState.update { state ->
						state.copy(
							playbackBitrate = format.bitrate.takeIf { it > 0 },
							playbackSampleRate = format.sampleRate.takeIf { it > 0 },
							playbackMimeType = format.sampleMimeType
						)
					}
					break
				}
			}
		}
	}

	override fun addToQueueSingle(song: DomainSong, notify: Boolean) = launchInView {
		controller?.addMediaItem(song.toMediaItem())
		_uiState.update { state ->
			val newQueue = state.queue + song
			state.copy(
				queue = newQueue,
				currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
				currentSong = if (state.currentIndex == -1) song else state.currentSong
			)
		}
		if (notify) snackBarManager.notifyAddedToQueue()
	}

	override fun addToQueue(collection: DomainSongCollection, notify: Boolean) {
		addToQueue(
			if (collection is DomainAlbum) collection.songs.sortedWith(
				compareBy(
					{ it.discNumber },
					{ it.trackNumber }
				)
			) else collection.songs,
			notify
		)
	}

	override fun addToQueue(songs: List<DomainSong>, notify: Boolean) = launchInView {
		val items = songs.map { it.toMediaItem() }
		controller?.addMediaItems(items)
		_uiState.update { state ->
			val newQueue = state.queue + songs
			state.copy(
				queue = newQueue,
				currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
				currentSong = if (state.currentIndex == -1) songs.firstOrNull() else state.currentSong
			)
		}
		if (notify) snackBarManager.notifyAddedToQueue()
	}

	override fun removeFromQueue(index: Int) = launchInView {
		controller?.removeMediaItem(index)
		_uiState.update { state ->
			val newQueue = state.queue.toMutableList().apply { removeAt(index) }
			val newIndex = when {
				index < state.currentIndex -> state.currentIndex - 1
				index == state.currentIndex -> if (newQueue.isEmpty()) -1 else state.currentIndex.coerceAtMost(
					newQueue.size - 1
				)

				else -> state.currentIndex
			}
			state.copy(
				queue = newQueue,
				currentIndex = newIndex,
				currentSong = if (newIndex == -1) null else newQueue[newIndex]
			)
		}
	}

	override fun moveQueueItem(fromIndex: Int, toIndex: Int) = launchInView {
		controller?.moveMediaItem(fromIndex, toIndex)
		_uiState.update { state ->
			val newQueue = state.queue.toMutableList().apply {
				val item = removeAt(fromIndex)
				add(toIndex, item)
			}
			val newIndex = when (state.currentIndex) {
				fromIndex -> toIndex
				in (fromIndex + 1)..toIndex -> state.currentIndex - 1
				in toIndex until fromIndex -> state.currentIndex + 1
				else -> state.currentIndex
			}
			state.copy(
				queue = newQueue,
				currentIndex = newIndex,
				currentSong = if (newIndex == -1) null else newQueue[newIndex]
			)
		}
	}

	override fun clearQueue() = launchInView {
		_uiState.update {
			it.copy(
				queue = emptyList(),
				currentSong = null,
				currentIndex = -1,
				progress = 0f
			)
		}
		controller?.clearMediaItems()
	}

	override fun playAt(index: Int) = launchInView {
		controller?.let { player ->
			if (index in 0 until player.mediaItemCount) {
				player.seekTo(index, 0L)
				player.play()
			}
		}
	}

	override fun getPlaybackPositionMs(): Long = controller?.currentPosition ?: 0L

	override fun playSongAtPosition(song: DomainSong, positionMs: Long) = launchInView {
		val mediaItem = withContext(Dispatchers.Default) { song.toMediaItem() }
		controller?.let { player ->
			player.stop()
			player.clearMediaItems()
			player.setMediaItem(mediaItem, positionMs)
			player.prepare()
			player.play()
		}
		_uiState.update { state ->
			state.copy(
				queue = listOf(song),
				currentIndex = 0,
				currentSong = song,
				isLoading = true
			)
		}
		checkAndAutoFillQueue()
	}

	override fun playNextSingle(song: DomainSong) = launchInView {
		controller?.addMediaItem(
			_uiState.value.currentIndex + 1,
            withContext(Dispatchers.Default) { song.toMediaItem() }
		)
		_uiState.update { state ->
			val newQueue =
				if (state.queue.isEmpty())
					state.queue + song
				else
					state.queue.slice(0..state.currentIndex) + song + state.queue.slice(state.currentIndex + 1..<state.queue.size)
			state.copy(
				queue = newQueue,
				currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
				currentSong = if (state.currentIndex == -1) song else state.currentSong
			)
		}
		snackBarManager.notifyPlayNext()
	}

	override fun playNext(collection: DomainSongCollection) = launchInView {
		val (items, newCollection) = withContext(Dispatchers.Default) {
            val newCollection =
                if (collection is DomainAlbum) collection.songs.sortedWith(
                    compareBy(
                        { it.discNumber },
                        { it.trackNumber }
                    )) else collection.songs
            newCollection.map { it.toMediaItem() } to newCollection
        }
		controller?.addMediaItems(_uiState.value.currentIndex + 1, items)
		_uiState.update { state ->
			val newQueue =
				if (state.queue.isEmpty())
					state.queue + newCollection
				else
					state.queue.slice(0..state.currentIndex) + newCollection + state.queue.slice(
						state.currentIndex + 1..<state.queue.size
					)
			state.copy(
				queue = newQueue,
				currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
				currentSong = if (state.currentIndex == -1) newCollection.firstOrNull() else state.currentSong
			)
		}
		snackBarManager.notifyPlayNext()
	}

	override fun playRadio(radio: DomainRadio) = launchInView {
		val dummyRadioSong = radio.toPlaybackSong()
		val mediaItem = mediaItemFactory.create(dummyRadioSong)

		controller?.let { player ->
			player.stop()
			player.clearMediaItems()
			player.setMediaItem(mediaItem)
			player.prepare()
			player.play()
		}

		_uiState.update { state ->
			state.copy(
				queue = listOf(dummyRadioSong),
				currentIndex = 0,
				currentSong = dummyRadioSong,
				isLoading = true
			)
		}
	}

	override fun shufflePlay(collection: DomainSongCollection) = launchInView {
		val (shuffledSongs, mediaItems) = withContext(Dispatchers.Default) {
            val songs = collection.songs.shuffled()
            songs to songs.map { it.toMediaItem() }
        }

		controller?.let { player ->
			player.shuffleModeEnabled = false
			player.setMediaItems(mediaItems, 0, 0L)
			player.prepare()
			player.play()
		}

		_uiState.update { state ->
			state.copy(
				queue = shuffledSongs,
				currentIndex = 0,
				currentSong = shuffledSongs.firstOrNull()
			)
		}
	}

	override fun pause() = launchInView(true) {
		controller?.pause()
	}

	override fun resume() = launchInView(true) {
		controller?.let { player ->
			if (player.playbackState == Player.STATE_ENDED) {
				player.seekTo(0, 0L)
			}
			player.play()
		}
	}

	override fun next() = launchInView(true) {
		if (controller?.hasNextMediaItem() == true) controller?.seekToNextMediaItem()
	}

	override fun previous() = launchInView(true) {
		val controller = controller ?: return@launchInView
		if (controller.hasPreviousMediaItem() && controller.currentPosition <= 1000) {
			controller.seekToPreviousMediaItem()
		} else {
			controller.seekTo(0)
		}
	}

	override fun toggleShuffle() {
		viewModelScope.launch {
			controller?.let { player ->
				player.shuffleModeEnabled = !player.shuffleModeEnabled
			}
		}
	}

	override fun toggleRepeat() = launchInView {
		controller?.let { player ->
			player.repeatMode = when (player.repeatMode) {
				Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
				Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
				else -> Player.REPEAT_MODE_OFF
			}
		}
	}

	override fun seek(normalized: Float) = launchInView(true) {
		controller?.let {
			val target = (it.duration * normalized).toLong()
			it.seekTo(target)
			_uiState.update { state ->
				state.copy(progress = normalized)
			}
		}

	}

	override fun onCleared() = launchInView {
		super.onCleared()
		controllerFuture?.let { MediaController.releaseFuture(it) }
	}

	override fun setPlaybackSpeed(value: Float) = launchInView {
		controller?.setPlaybackSpeed(value)
		_uiState.update { it.copy(playbackSpeed = value) }
	}

	private fun DomainSong.toMediaItem(): MediaItem = mediaItemFactory.create(this)
}

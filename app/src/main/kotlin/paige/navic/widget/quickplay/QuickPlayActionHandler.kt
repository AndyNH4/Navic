/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.quickplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import paige.navic.domain.model.DomainSong
import paige.navic.domain.repository.PlayerStateRepository
import paige.navic.exoplayer.PlaybackMediaItemFactory
import paige.navic.exoplayer.toPlaybackSong
import paige.navic.widget.action.WidgetController

class QuickPlayActionHandler(
	private val repository: QuickPlayRepository,
	private val mediaItemFactory: PlaybackMediaItemFactory,
	private val controller: WidgetController,
	private val playerStateRepository: PlayerStateRepository
) {
	suspend fun play(type: QuickPlaySourceType, id: String? = null) {
		when (val playback = repository.getPlayback(type, id)) {
			QuickPlayPlayback.Continue -> controller.withController { player ->
				player.play()
			}

			is QuickPlayPlayback.Songs -> {
				val items = withContext(Dispatchers.Default) {
					playback.songs.map(mediaItemFactory::create)
				}
				controller.withController { player ->
					player.shuffleModeEnabled = false
					player.setMediaItems(items, 0, 0L)
					player.prepare()
					player.play()
				}
				persistQueue(playback.songs)
			}

			is QuickPlayPlayback.RadioStation -> {
				val song = playback.radio.toPlaybackSong()
				val item = mediaItemFactory.create(song)
				controller.withController { player ->
					player.shuffleModeEnabled = false
					player.setMediaItem(item)
					player.prepare()
					player.play()
				}
				persistQueue(listOf(song))
			}
		}
	}

	private suspend fun persistQueue(songs: List<DomainSong>) {
		val previous = playerStateRepository.state.first()
		playerStateRepository.setState(
			previous.copy(
				queue = songs,
				currentSong = songs.first(),
				currentCollection = null,
				currentIndex = 0,
				isPaused = false,
				progress = 0f,
				isLoading = true,
				isShuffleEnabled = false
			)
		)
	}
}

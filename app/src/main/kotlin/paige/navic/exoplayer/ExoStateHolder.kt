/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.exoplayer

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ktor.KtorDataSource
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.wav.WavExtractor
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import coil3.ImageLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.exoplayer.impl.ExoArtworkLoader
import paige.navic.exoplayer.impl.CrossfadeEngine
import paige.navic.exoplayer.impl.CrossfadeForwardingPlayer
import paige.navic.exoplayer.impl.ExoAudioGainProcessor
import paige.navic.util.createHttpClientWithPreferences

@OptIn(UnstableApi::class)
class ExoStateHolder: KoinComponent {
	private val context: Context by inject()
	private val preferenceManager: PreferenceManager by inject()
	private val imageLoader: ImageLoader by inject()
	private val mutex = Mutex()

	private var isInitialized = false

	val isReady: Boolean
		get() = isInitialized

	// ExoPlaybackService and AudioGainManager depends on these fields below
	// playerInstance is the MediaSession-stable wrapper around the engine's
	// leading leg; all Player-interface consumers use it transparently
	lateinit var playerInstance: Player
	lateinit var mediaSession: MediaSession
	private lateinit var sessionWrapper: CrossfadeForwardingPlayer
	private lateinit var crossfadeEngine: CrossfadeEngine

	// observers notified for every crossfade-engine leg, existing and future
	// (leg B is created lazily by the engine); ExoPlaybackService uses this to
	// attach the equalizer to both legs
	private val legObservers = mutableListOf<(ExoPlayer) -> Unit>()
	private val engineLegs = mutableListOf<ExoPlayer>()

	fun addLegObserver(observer: (ExoPlayer) -> Unit) {
		legObservers.add(observer)
		engineLegs.forEach(observer)
	}

	fun removeLegObserver(observer: (ExoPlayer) -> Unit) {
		legObservers.remove(observer)
	}

	private fun registerLeg(player: ExoPlayer): ExoPlayer {
		engineLegs.add(player)
		legObservers.forEach { it(player) }
		return player
	}

	// one gain processor per engine leg: a single AudioProcessor instance
	// cannot feed two AudioSinks concurrently
	val gainProcessor = ExoAudioGainProcessor()
	val gainProcessorB = ExoAudioGainProcessor()

	private val audioAttributes = AudioAttributes.Builder()
		.setUsage(C.USAGE_MEDIA)
		.setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
		.build()


	private class MediaSessionCallback : MediaSession.Callback {
		override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
		): MediaSession.ConnectionResult {
			val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
				.buildUpon()
				.add(SessionCommand(COMMAND_SHUFFLE, Bundle.EMPTY))
				.add(SessionCommand(COMMAND_REPEAT, Bundle.EMPTY))
				.build()

			return MediaSession.ConnectionResult.accept(
				sessionCommands,
				MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
			)
		}

		override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
		): ListenableFuture<SessionResult> {
			// operate on the session's current player: while casting this is the
			// RemoteCastPlayer, not the local ExoPlayer
			val player = session.player
			when (customCommand.customAction) {
				COMMAND_SHUFFLE -> {
					player.shuffleModeEnabled = !player.shuffleModeEnabled
				}

				COMMAND_REPEAT -> {
					player.repeatMode = when (player.repeatMode) {
						Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
						Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
						else -> Player.REPEAT_MODE_OFF
					}
				}
			}

			return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
		}
	}

	private val extractorsFactory = ExtractorsFactory {
        arrayOf(
            FlacExtractor(),
            WavExtractor(),
            FragmentedMp4Extractor(DefaultSubtitleParserFactory.UNSUPPORTED),
            Mp4Extractor(DefaultSubtitleParserFactory.UNSUPPORTED),
            OggExtractor(),
            MatroskaExtractor(DefaultSubtitleParserFactory.UNSUPPORTED),
            AdtsExtractor(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING),
            Mp3Extractor(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
        )
    }

	private val httpDataSourceFactory = KtorDataSource.Factory(
        createHttpClientWithPreferences(
            preferenceManager
        )
    )

	private fun createRenderers(context: Context, gainProcessor: ExoAudioGainProcessor): RenderersFactory {
		val audioProcessors = arrayOf<AudioProcessor>(gainProcessor)
		return RenderersFactory { handler, _, audioListener, _, _ ->
			arrayOf<BaseRenderer>(
				FfmpegAudioRenderer(
					context,
					handler,
					audioListener,
					*audioProcessors
				),
				MediaCodecAudioRenderer(
					context,
					MediaCodecSelector.DEFAULT,
					handler,
					audioListener,
					DefaultAudioSink.Builder(context)
						.setAudioProcessors(audioProcessors)
						.build()
				)
			)
		}
	}

	private fun makeButtons(player: Player) = buildList {
		add(
			CommandButton.Builder(
				if (player.shuffleModeEnabled) {
					CommandButton.ICON_SHUFFLE_ON
				} else {
					CommandButton.ICON_SHUFFLE_OFF
				}
			)
				.setDisplayName("Shuffle")
				.setSessionCommand(SessionCommand(COMMAND_SHUFFLE, Bundle.EMPTY))
				.build()
		)

		add(
			CommandButton.Builder(
				when (player.repeatMode) {
					Player.REPEAT_MODE_OFF -> CommandButton.ICON_REPEAT_OFF
					Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
					else -> CommandButton.ICON_REPEAT_ONE
				}
			)
				.setDisplayName("Repeat")
				.setSessionCommand(SessionCommand(COMMAND_REPEAT, Bundle.EMPTY))
				.build()
		)
	}

	// per-leg factory: a LoadControl instance must not be shared between players
	private fun createLoadControl(): LoadControl = DefaultLoadControl.Builder().apply {
		setBufferDurationsMs(
			/* minBufferMs = */ 32_000,
			/* maxBufferMs = */ 64_000,
			/* bufferForPlaybackMs = */ 2_500,
			/* bufferForPlaybackAfterRebufferMs = */ 5_000
		)
		setBackBuffer(10_000, true)
	}.build()

	private fun createLegPlayer(
		gainProcessor: ExoAudioGainProcessor,
		handleAudioFocus: Boolean
	): ExoPlayer {
		val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
		val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
		return ExoPlayer.Builder(
			context,
			createRenderers(context, gainProcessor)
		).apply {
			setLoadControl(createLoadControl())
			setMediaSourceFactory(mediaSourceFactory)
			setHandleAudioBecomingNoisy(true)
			setWakeMode(C.WAKE_MODE_NETWORK)
		}.build()
			.apply {
				setAudioAttributes(audioAttributes, handleAudioFocus)

				trackSelectionParameters =
					trackSelectionParameters.buildUpon().setAudioOffloadPreferences(
						TrackSelectionParameters.AudioOffloadPreferences
							.Builder()
							.setIsGaplessSupportRequired(preferenceManager.gaplessPlayback)
							.setAudioOffloadMode(
								if (preferenceManager.audioOffload) {
									TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
								} else {
									TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
								}
							)
							.build()
					).build()
			}
	}

	private suspend fun createPlayerInstance(): Player = mutex.withLock {
		val leadingLeg = registerLeg(createLegPlayer(gainProcessor, handleAudioFocus = true))
		sessionWrapper = CrossfadeForwardingPlayer(leadingLeg)
		playerInstance = sessionWrapper
		crossfadeEngine = CrossfadeEngine(
			preferenceManager = preferenceManager,
			audioAttributes = audioAttributes,
			legA = leadingLeg,
			standbyLegFactory = {
				registerLeg(createLegPlayer(gainProcessorB, handleAudioFocus = false))
			},
			wrapper = sessionWrapper
		).apply { start() }

		sessionWrapper.addListener(object : Player.Listener {
			override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
				refreshSessionButtons()
			}

			override fun onRepeatModeChanged(repeatMode: Int) {
				refreshSessionButtons()
			}
		})

		return playerInstance
	}

	private suspend fun createMediaSession(): MediaSession = mutex.withLock {
		val bitmapLoader = ExoArtworkLoader(
			context,
			dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory),
			imageLoader
		)
		val sessionIntent = context.packageManager
			.getLaunchIntentForPackage(context.packageName)
			?.apply {
				flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
					Intent.FLAG_ACTIVITY_CLEAR_TOP
			}

		val sessionPendingIntent = PendingIntent.getActivity(
			context,
			0,
			sessionIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		this.playerInstance.let {
			mediaSession = MediaSession.Builder(context, it)
				.setSessionActivity(sessionPendingIntent)
				.setBitmapLoader(bitmapLoader)
				.setCallback(MediaSessionCallback())
				.setCustomLayout(makeButtons(it))
				.build()
		}

		return mediaSession
	}

	/**
	 * initializes instances for the player and the media session, should be called ASAP on PlaybackService (onCreate)
	 */
	fun initState() = runBlocking {
        if (isInitialized) return@runBlocking

        createPlayerInstance()
        createMediaSession()
        isInitialized = !isInitialized
    }

	/** Called from settings when crossfade preferences change; safe before initState(). */
	fun onCrossfadeSettingsChanged() {
		if (!isInitialized) return
		crossfadeEngine.onSettingsChanged()
	}

	/**
	 * Rebuilds the notification's shuffle/repeat buttons from the session's
	 * current player, which is the RemoteCastPlayer while casting.
	 */
	fun refreshSessionButtons() {
		if (!isInitialized) return
		mediaSession.setCustomLayout(makeButtons(mediaSession.player))
	}

	fun destroySession() {
		if (!isInitialized) return

		mediaSession.let {
			// a remote (cast) player may be attached; detach it so the local
			// wrapper (and through it the engine's legs) is released below
			if (it.player !== playerInstance) {
				runCatching { it.setPlayer(playerInstance) }
			}
			it.release()
		}
		// stops the fade machinery and releases both legs
		crossfadeEngine.release()
		engineLegs.clear()
		playerInstance.release()
		isInitialized = !isInitialized
	}

	companion object {
		const val COMMAND_SHUFFLE = "COMMAND_SHUFFLE"
		const val COMMAND_REPEAT = "COMMAND_REPEAT"

		fun newSessionToken(context: Context): SessionToken {
			return SessionToken(context, ComponentName(context, ExoPlaybackService::class.java))
		}
	}
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.exoplayer

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.model.DomainExplicitStatus
import paige.navic.domain.model.DomainRadio
import paige.navic.domain.model.DomainSong
import java.io.File
import kotlin.time.Duration

class PlaybackMediaItemFactory(
	private val connectivityManager: ConnectivityManager,
	private val preferenceManager: PreferenceManager,
	private val sessionManager: SessionManager,
	private val downloadManager: DownloadManager
) {
	fun create(song: DomainSong): MediaItem {
		val displayArtist = song.artists.joinToString { it.name }.ifBlank { song.artistName }
		val albumArtistName = song.albumArtists.joinToString { it.name }.ifBlank { song.artistName }

		val metadata = MediaMetadata.Builder()
			.setTitle(song.title)
			.setSubtitle(displayArtist)
			.setArtist(displayArtist)
			.setAlbumArtist(albumArtistName)
			.setAlbumTitle(song.albumTitle)
			.setDurationMs(song.duration.inWholeMilliseconds)
			.setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
			.setArtworkUri(song.coverArtId?.let { sessionManager.getCoverArtUrl(it).toUri() })
			.build()

		val uri = when {
			song.id.startsWith(RADIO_ID_PREFIX) && !song.filePath.isNullOrEmpty() -> {
				song.filePath.toUri()
			}

			else -> {
				val localPath = downloadManager.getDownloadedFilePath(song.id)
				if (localPath != null) File(localPath).toUri() else getStreamUrl(song.id)
			}
		}

		return MediaItem.Builder()
			.setUri(uri)
			.setMediaId(song.id)
			.setMediaMetadata(metadata)
			.apply {
				if (song.id.startsWith(RADIO_ID_PREFIX)) {
					setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
				}
			}
			.build()
	}

	private fun getStreamUrl(id: String): Uri {
		val isCellular = connectivityManager.isCellular.value
		val bitrate = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.bitrate else preferenceManager.streamingQualityWifi.bitrate
		}
		val container = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customFormatCellular else preferenceManager.customFormatWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.container else preferenceManager.streamingQualityWifi.container
		}
		return sessionManager.api.getStreamUrl(id, bitrate, container?.takeIf { it.isNotBlank() })
			.toUri()
			.buildUpon()
			.appendQueryParameter("estimateContentLength", "true")
			.build()
	}
}

fun DomainRadio.toPlaybackSong(): DomainSong = DomainSong(
	id = playbackRadioId(name),
	title = name,
	artistName = "Live Radio",
	albumId = "radio_album",
	albumTitle = "Live Stream",
	duration = Duration.ZERO,
	trackNumber = 1,
	coverArtId = null,
	artistId = "",
	parentId = "",
	comment = null,
	discNumber = null,
	isrc = emptyList(),
	year = null,
	genre = null,
	genres = emptyList(),
	moods = emptyList(),
	bpm = null,
	contributors = emptyList(),
	playCount = 0,
	userRating = 0,
	averageRating = null,
	bitRate = null,
	bitDepth = null,
	sampleRate = null,
	audioChannelCount = null,
	replayGain = null,
	fileSize = 0,
	fileExtension = "",
	mimeType = "",
	filePath = streamUrl,
	starredAt = null,
	musicBrainzId = null,
	explicitStatus = DomainExplicitStatus.Unknown,
	artists = emptyList(),
	albumArtists = emptyList(),
	isExternal = false
)

fun playbackRadioId(name: String): String = "$RADIO_ID_PREFIX${name.hashCode()}"

const val RADIO_ID_PREFIX = "radio_"

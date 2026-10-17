/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.last
import paige.navic.domain.model.DomainAlbum
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.model.DomainSong
import paige.navic.domain.repository.CollectionRepository
import paige.navic.domain.repository.SearchRepository
import paige.navic.ui.core.UiState
import paige.navic.util.Logger

data class VoicePlayRequest(
	val mediaName: String?,
	val artistName: String?,
	val albumName: String?
) {
	// the single most specific term, never a concatenation: concatenating
	// song+artist could never substring-match a single field server-side
	val primaryQuery: String
		get() = mediaName ?: artistName ?: albumName ?: ""
}

sealed interface VoicePlayResolution {
	data class Play(val songs: List<DomainSong>, val label: String) : VoicePlayResolution
	data class OpenSearch(val query: String) : VoicePlayResolution
	data object RequiresLogin : VoicePlayResolution
}

/**
 * Receives Google Assistant app action deep links (rezeener://play, declared in
 * res/xml/shortcuts.xml for actions.intent.PLAY_MEDIA) and turns them into a
 * playback decision. Matching is deliberately conservative: only an exact,
 * unambiguous match plays anything, everything else lands on the search
 * screen with the query prefilled.
 */
class VoiceActionManager(
	private val searchRepository: SearchRepository,
	private val collectionRepository: CollectionRepository,
	private val artistGroupManager: ArtistGroupManager,
	private val sessionManager: SessionManager,
	connectivityManager: ConnectivityManager
) {
	val pendingAction: StateFlow<VoicePlayRequest?>
		field = MutableStateFlow(null)

	private val isOnline = connectivityManager.isOnline

	/** Returns true when the intent carried a voice deep link worth acting on. */
	fun handleIntent(intent: Intent?): Boolean {
		if (intent?.action != Intent.ACTION_VIEW) return false
		val uri = intent.data ?: return false
		if (uri.scheme != SCHEME || uri.host != HOST) return false

		// url-template fulfillment delivers query params; the plain intent
		// fulfillment delivers extras keyed by the dotted parameter names
		fun param(key: String, extraKey: String): String? =
			(uri.getQueryParameter(key) ?: intent.getStringExtra(extraKey))
				?.trim()
				?.takeIf { it.isNotEmpty() }

		val request = VoicePlayRequest(
			mediaName = param("mediaName", "media.name"),
			artistName = param("artistName", "media.byArtist.name"),
			albumName = param("albumName", "media.inAlbum.name")
		)
		if (request.mediaName == null && request.artistName == null && request.albumName == null) {
			return false
		}
		pendingAction.value = request
		return true
	}

	fun consume() {
		pendingAction.value = null
	}

	suspend fun resolve(request: VoicePlayRequest): VoicePlayResolution {
		if (!sessionManager.isLoggedIn.value) return VoicePlayResolution.RequiresLogin

		val query = request.primaryQuery
		if (!isOnline.value) return VoicePlayResolution.OpenSearch(query)

		val results = try {
			searchRepository.search(query)
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.e("VoiceActionManager", "voice search failed", e)
			return VoicePlayResolution.OpenSearch(query)
		}

		val songs = results.filterIsInstance<DomainSong>()
		val albums = results.filterIsInstance<DomainAlbum>()
		val artists = results.filterIsInstance<DomainArtist>()

		resolveSong(request, songs)?.let { return it }
		resolveAlbum(request, albums)?.let { return it }
		resolveArtist(request, artists, songs)?.let { return it }

		return VoicePlayResolution.OpenSearch(query)
	}

	private fun resolveSong(
		request: VoicePlayRequest,
		songs: List<DomainSong>
	): VoicePlayResolution.Play? {
		val mediaName = request.mediaName ?: return null
		val exactMatches = songs.filter { song ->
			song.title.equals(mediaName, ignoreCase = true)
				&& request.artistName?.let { song.matchesArtist(it) } != false
				&& request.albumName?.let { song.albumTitle?.equals(it, ignoreCase = true) == true } != false
		}
		if (exactMatches.isEmpty()) return null
		// the same title from several artists and no artist given is ambiguous
		if (request.artistName == null
			&& exactMatches.distinctBy { it.artistName?.lowercase() }.size > 1) {
			return null
		}
		return VoicePlayResolution.Play(exactMatches, mediaName)
	}

	private suspend fun resolveAlbum(
		request: VoicePlayRequest,
		albums: List<DomainAlbum>
	): VoicePlayResolution.Play? {
		val albumName = request.albumName ?: request.mediaName ?: return null
		val exactMatches = albums.filter { album ->
			album.name?.equals(albumName, ignoreCase = true) == true
				&& request.artistName?.let { album.artistName?.equals(it, ignoreCase = true) == true } != false
		}
		val album = exactMatches.singleOrNull() ?: return null
		// search results only carry locally cached tracks, pull the full tracklist
		val collection = try {
			collectionRepository.getCollectionFlow(fullRefresh = true, collectionId = album.id)
				.last()
				.let { (it as? UiState.Success)?.data ?: (it as? UiState.Error)?.data }
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			null
		} ?: return null
		if (collection.songs.isEmpty()) return null
		return VoicePlayResolution.Play(collection.songs, album.name ?: albumName)
	}

	private suspend fun resolveArtist(
		request: VoicePlayRequest,
		artists: List<DomainArtist>,
		songs: List<DomainSong>
	): VoicePlayResolution.Play? {
		val artistName = request.artistName ?: request.mediaName ?: return null
		val matches = mutableListOf<Pair<DomainArtist, List<String>>>()
		artists.forEach { artist ->
			val names = listOf(artist.name) + artistGroupManager.getGroupInfo(artist.id)
				?.members?.map { it.name }.orEmpty()
			if (names.any { it.equals(artistName, ignoreCase = true) }) {
				matches += artist to names
			}
		}
		val (artist, artistNames) = matches.singleOrNull() ?: return null
		val artistSongs = songs.filter { song ->
			artistNames.any { song.matchesArtist(it) }
		}
		if (artistSongs.isEmpty()) return null
		return VoicePlayResolution.Play(artistSongs, artist.name)
	}

	private fun DomainSong.matchesArtist(name: String): Boolean =
		artistName?.equals(name, ignoreCase = true) == true
			|| artists.any { it.name.equals(name, ignoreCase = true) }

	companion object {
		private const val SCHEME = "rezeener"
		private const val HOST = "play"
	}
}

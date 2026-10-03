/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.repository

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.DomainSong
import paige.navic.util.createHttpClientWithPreferences

/**
 * Talks to a self-hosted AudioMuse-AI instance (sonic similarity analysis).
 * Returns media-server song ids that can be resolved against the local library.
 */
class AudioMuseRepository(
	private val preferenceManager: PreferenceManager,
	private val songDao: SongDao
) {
	private val json = Json { ignoreUnknownKeys = true }

	private val client = createHttpClientWithPreferences(preferenceManager).config {
		install(HttpTimeout) {
			requestTimeoutMillis = 20000
			connectTimeoutMillis = 10000
			socketTimeoutMillis = 20000
		}
	}

	val isConfigured: Boolean
		get() = preferenceManager.audioMuseUrl.isNotBlank()

	/**
	 * Returns songs sonically similar to [songId] in similarity order.
	 * Ids that are not part of the local library are dropped.
	 * Throws on network/API failure so callers can surface an error state.
	 */
	suspend fun getSimilarSongs(songId: String, count: Int = 30): List<DomainSong> =
		withContext(Dispatchers.IO) {
			val baseUrl = preferenceManager.audioMuseUrl.trim().removeSuffix("/")
			check(baseUrl.isNotEmpty()) { "AudioMuse-AI URL is not configured" }

			val response = client.get("$baseUrl/api/similar_tracks") {
				parameter("item_id", songId)
				parameter("n", count)
				parameter("eliminate_duplicates", true)
				accept(ContentType.Application.Json)
			}
			check(response.status.isSuccess()) {
				"AudioMuse-AI returned status ${response.status.value}"
			}

			val ids = json.parseToJsonElement(response.bodyAsText()).jsonArray
				.mapNotNull { it.jsonObject["item_id"]?.jsonPrimitive?.contentOrNull }
				.filter { it.isNotBlank() }
				.distinct()

			val localById = songDao.getSongsByIds(ids)
				.associateBy { it.songId }
			ids.mapNotNull { localById[it]?.toDomainModel() }
		}
}

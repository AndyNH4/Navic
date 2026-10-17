/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.network

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.util.Logger
import paige.navic.util.createHttpClientWithPreferences
import paige.navic.util.md5Hex
import java.security.SecureRandom
import kotlin.time.Instant

class SubsonicScrobbleApi(
	private val sessionManager: SessionManager,
	preferenceManager: PreferenceManager
) {
	private val client = createHttpClientWithPreferences(preferenceManager)
	private val json = Json { ignoreUnknownKeys = true }

	suspend fun scrobble(
		songId: String,
		time: Instant?,
		submission: Boolean
	): Result<Unit> = runCatching {
		val (username, password) = sessionManager.getCredentials()
			?: throw Exception("No credentials available")
		val baseUrl = sessionManager.activeInstanceUrl.value.trimEnd('/')
		if (baseUrl.isBlank()) throw Exception("No active instance URL")

		val salt = generateSalt()
		val response = client.get("$baseUrl/rest/scrobble.view") {
			parameter("id", songId)
			parameter("submission", submission)
			if (time != null) parameter("time", time.toEpochMilliseconds())
			parameter("u", username)
			parameter("t", md5Hex(password + salt))
			parameter("s", salt)
			parameter("v", "1.16.1")
			parameter("c", "Navic")
			parameter("f", "json")
		}

		val root = json.parseToJsonElement(response.bodyAsText())
			.jsonObject["subsonic-response"]?.jsonObject
			?: throw Exception("Malformed Subsonic response")

		if (root["status"]?.jsonPrimitive?.content != "ok") {
			val message = root["error"]?.jsonObject
				?.get("message")?.jsonPrimitive?.content
			throw Exception(message ?: "Scrobble request failed")
		}
	}.onFailure {
		Logger.e("SubsonicScrobbleApi", "scrobble failed for $songId", it)
	}

	private fun generateSalt(): String {
		val bytes = ByteArray(12)
		SecureRandom().nextBytes(bytes)
		return bytes.joinToString("") { "%02x".format(it) }
	}
}

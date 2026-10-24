/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.network

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import paige.navic.domain.manager.PreferenceManager
import paige.navic.util.Logger
import paige.navic.util.createHttpClientWithPreferences
import paige.navic.util.md5Hex
import java.security.SecureRandom

data class ServerCapabilities(
	val serverType: String? = null,
	val serverVersion: String? = null,
	val navidromeNativeAvailable: Boolean = false,
	val sonicSimilarityAvailable: Boolean = false
)

internal class ServerCapabilityDetector(
	private val settings: Settings,
	preferenceManager: PreferenceManager
) {
	private val client = createHttpClientWithPreferences(preferenceManager)
	private val json = Json { ignoreUnknownKeys = true }

	val capabilities: StateFlow<ServerCapabilities>
		field = MutableStateFlow(loadPersistedCapabilities())

	suspend fun detect(baseUrl: String, credentials: Pair<String, String>) {
		val detected = try {
			val (username, password) = credentials
			val normalizedBaseUrl = baseUrl.trimEnd('/')
			val pingSalt = generateSalt()
			val response = client.get("$normalizedBaseUrl/rest/ping.view") {
				parameter("f", "json")
				parameter("u", username)
				parameter("t", md5Hex(password + pingSalt))
				parameter("s", pingSalt)
				parameter("v", "1.16.1")
				parameter("c", "Navic")
			}
			if (!response.status.isSuccess()) {
				throw Exception("Capability probe returned HTTP ${response.status.value}")
			}

			val root = json.parseToJsonElement(response.bodyAsText())
				.jsonObject["subsonic-response"]?.jsonObject
				?: throw Exception("Malformed Subsonic ping response")
			if (root["status"]?.jsonPrimitive?.content != "ok") {
				throw Exception("Subsonic ping failed")
			}

			val sonicSimilarityAvailable = detectSonicSimilarity(
				baseUrl = normalizedBaseUrl,
				username = username,
				password = password
			)

			createCapabilities(
				serverType = root["type"]?.jsonPrimitive?.contentOrNull,
				serverVersion = root["serverVersion"]?.jsonPrimitive?.contentOrNull,
				sonicSimilarityAvailable = sonicSimilarityAvailable
			)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.w("ServerCapabilityDetector", "Server capability probe failed", e)
			ServerCapabilities()
		}

		capabilities.value = detected
		settings[SERVER_TYPE_KEY] = detected.serverType
		settings[SERVER_VERSION_KEY] = detected.serverVersion
		settings[SONIC_SIMILARITY_KEY] = detected.sonicSimilarityAvailable
	}

	fun clear() {
		capabilities.value = ServerCapabilities()
		settings[SERVER_TYPE_KEY] = null
		settings[SERVER_VERSION_KEY] = null
		settings.remove(SONIC_SIMILARITY_KEY)
	}

	private suspend fun detectSonicSimilarity(
		baseUrl: String,
		username: String,
		password: String
	): Boolean = runCatching {
		val salt = generateSalt()
		val response = client.get("$baseUrl/rest/getOpenSubsonicExtensions.view") {
			parameter("f", "json")
			parameter("u", username)
			parameter("t", md5Hex(password + salt))
			parameter("s", salt)
			parameter("v", "1.16.1")
			parameter("c", "Navic")
		}
		if (!response.status.isSuccess()) return@runCatching false

		json.parseToJsonElement(response.bodyAsText())
			.jsonObject["subsonic-response"]?.jsonObject
			?.get("openSubsonicExtensions")?.jsonArray
			?.any {
				it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
					.equals("sonicSimilarity", ignoreCase = true)
			} == true
	}.getOrDefault(false)

	private fun loadPersistedCapabilities() = createCapabilities(
		serverType = settings.getStringOrNull(SERVER_TYPE_KEY),
		serverVersion = settings.getStringOrNull(SERVER_VERSION_KEY),
		sonicSimilarityAvailable = settings.getBoolean(SONIC_SIMILARITY_KEY, false)
	)

	private fun createCapabilities(
		serverType: String?,
		serverVersion: String?,
		sonicSimilarityAvailable: Boolean = false
	): ServerCapabilities {
		val cleanType = serverType?.trim()?.takeIf { it.isNotEmpty() }
		val cleanVersion = serverVersion?.trim()?.takeIf { it.isNotEmpty() }
		return ServerCapabilities(
			serverType = cleanType,
			serverVersion = cleanVersion,
			navidromeNativeAvailable = cleanType.equals("navidrome", ignoreCase = true)
				&& supportsNativeApi(cleanVersion),
			sonicSimilarityAvailable = sonicSimilarityAvailable
		)
	}

	private fun supportsNativeApi(version: String?): Boolean {
		val match = VERSION_REGEX.matchEntire(version?.trim().orEmpty()) ?: return false
		val major = match.groupValues[1].toIntOrNull() ?: return false
		val minor = match.groupValues[2].toIntOrNull() ?: return false
		match.groupValues[3].ifEmpty { "0" }.toIntOrNull() ?: return false
		return major > 0 || minor >= 59
	}

	private fun generateSalt(): String {
		val bytes = ByteArray(12)
		SecureRandom().nextBytes(bytes)
		return bytes.joinToString("") { "%02x".format(it) }
	}

	private companion object {
		const val SERVER_TYPE_KEY = "detectedServerType"
		const val SERVER_VERSION_KEY = "detectedServerVersion"
		const val SONIC_SIMILARITY_KEY = "detectedSonicSimilarity"
		val VERSION_REGEX = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:\\s*\\([^)]*\\))?(?:[-+].*)?$")
	}
}

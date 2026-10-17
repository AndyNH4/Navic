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
	val navidromeNativeAvailable: Boolean = false
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
			val salt = generateSalt()
			val response = client.get("${baseUrl.trimEnd('/')}/rest/ping.view") {
				parameter("f", "json")
				parameter("u", username)
				parameter("t", md5Hex(password + salt))
				parameter("s", salt)
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

			createCapabilities(
				serverType = root["type"]?.jsonPrimitive?.contentOrNull,
				serverVersion = root["serverVersion"]?.jsonPrimitive?.contentOrNull
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
	}

	fun clear() {
		capabilities.value = ServerCapabilities()
		settings[SERVER_TYPE_KEY] = null
		settings[SERVER_VERSION_KEY] = null
	}

	private fun loadPersistedCapabilities() = createCapabilities(
		serverType = settings.getStringOrNull(SERVER_TYPE_KEY),
		serverVersion = settings.getStringOrNull(SERVER_VERSION_KEY)
	)

	private fun createCapabilities(serverType: String?, serverVersion: String?): ServerCapabilities {
		val cleanType = serverType?.trim()?.takeIf { it.isNotEmpty() }
		val cleanVersion = serverVersion?.trim()?.takeIf { it.isNotEmpty() }
		return ServerCapabilities(
			serverType = cleanType,
			serverVersion = cleanVersion,
			navidromeNativeAvailable = cleanType.equals("navidrome", ignoreCase = true)
				&& supportsNativeApi(cleanVersion)
		)
	}

	private fun supportsNativeApi(version: String?): Boolean {
		val match = VERSION_REGEX.matchEntire(version?.trim().orEmpty()) ?: return false
		val major = match.groupValues[1].toIntOrNull() ?: return false
		val minor = match.groupValues[2].toIntOrNull() ?: return false
		val patch = match.groupValues[3].ifEmpty { "0" }.toIntOrNull() ?: return false
		return major > 0 || major == 0 && (minor > 59 || minor == 59 && patch >= 0)
	}

	private fun generateSalt(): String {
		val bytes = ByteArray(12)
		SecureRandom().nextBytes(bytes)
		return bytes.joinToString("") { "%02x".format(it) }
	}

	private companion object {
		const val SERVER_TYPE_KEY = "detectedServerType"
		const val SERVER_VERSION_KEY = "detectedServerVersion"
		val VERSION_REGEX = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$")
	}
}

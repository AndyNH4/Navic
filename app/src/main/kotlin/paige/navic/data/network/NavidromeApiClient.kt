/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.data.network

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.util.Logger
import paige.navic.util.createHttpClientWithPreferences

@Serializable
data class NavidromeScrobble(
	val id: Long,
	val mediaFileId: String,
	@Serializable(with = FlexibleLongSerializer::class)
	val submissionTime: Long
)

@Serializable
private data class NavidromeLoginRequest(
	val username: String,
	val password: String
)

@Serializable
private data class NavidromeLoginResponse(
	val token: String
)

class NavidromeApiClient(
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager
) {
	private val client = createHttpClientWithPreferences(preferenceManager).config {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
	}

	private var token: String? = null
	private var tokenBaseUrl: String? = null
	private var tokenUsername: String? = null

	suspend fun getScrobbles(
		fromSeconds: Long? = null,
		start: Int = 0,
		end: Int = 500
	): Result<List<NavidromeScrobble>> {
		if (!preferenceManager.navidromeEnhancementEnabled
			|| !sessionManager.serverCapabilities.value.navidromeNativeAvailable
		) {
			val error = IllegalStateException("Navidrome native API is unavailable")
			Logger.w("NavidromeApiClient", error.message.orEmpty())
			return Result.failure(error)
		}

		return runCatching<List<NavidromeScrobble>> {
			val credentials = sessionManager.getCredentials()
				?: throw Exception("No credentials available")
			val baseUrl = sessionManager.activeInstanceUrl.value.trimEnd('/')
			if (baseUrl.isBlank()) throw Exception("No active instance URL")

			var activeToken = token
			if (activeToken == null || tokenBaseUrl != baseUrl || tokenUsername != credentials.first) {
				activeToken = login(baseUrl, credentials)
			}

			var response = requestScrobbles(baseUrl, activeToken, fromSeconds, start, end)
			storeRefreshedToken(response, baseUrl, credentials.first)
			if (response.status == HttpStatusCode.Unauthorized) {
				response.bodyAsText()
				token = null
				activeToken = login(baseUrl, credentials)
				response = requestScrobbles(baseUrl, activeToken, fromSeconds, start, end)
				storeRefreshedToken(response, baseUrl, credentials.first)
			}

			if (!response.status.isSuccess()) {
				throw Exception("Navidrome scrobble request returned HTTP ${response.status.value}")
			}
			response.body()
		}.onFailure {
			Logger.e("NavidromeApiClient", "Failed to fetch scrobbles", it)
		}
	}

	private suspend fun login(baseUrl: String, credentials: Pair<String, String>): String {
		val response = client.post("$baseUrl/auth/login") {
			contentType(ContentType.Application.Json)
			setBody(
				NavidromeLoginRequest(
					username = credentials.first,
					password = credentials.second
				)
			)
		}
		if (!response.status.isSuccess()) {
			throw Exception("Navidrome login returned HTTP ${response.status.value}")
		}

		val loginToken = response.body<NavidromeLoginResponse>().token
			.ifBlank { throw Exception("Navidrome login returned an empty token") }
		storeToken(loginToken, baseUrl, credentials.first)
		storeRefreshedToken(response, baseUrl, credentials.first)
		return token ?: loginToken
	}

	private suspend fun requestScrobbles(
		baseUrl: String,
		activeToken: String,
		fromSeconds: Long?,
		start: Int,
		end: Int
	): HttpResponse = client.get("$baseUrl/api/scrobble") {
		header(NAVIDROME_AUTH_HEADER, "Bearer $activeToken")
		parameter("_start", start)
		parameter("_end", end)
		parameter("_sort", "submission_time")
		parameter("_order", "ASC")
		if (fromSeconds != null) parameter("from", fromSeconds)
	}

	private fun storeRefreshedToken(response: HttpResponse, baseUrl: String, username: String) {
		val header = response.headers[NAVIDROME_AUTH_HEADER]?.trim() ?: return
		val refreshed = if (header.startsWith("Bearer ", ignoreCase = true)) {
			header.substring(7).trim()
		} else {
			header
		}
		if (refreshed.isNotEmpty()) storeToken(refreshed, baseUrl, username)
	}

	private fun storeToken(value: String, baseUrl: String, username: String) {
		token = value
		tokenBaseUrl = baseUrl
		tokenUsername = username
	}

	private companion object {
		const val NAVIDROME_AUTH_HEADER = "X-ND-Authorization"
	}
}

private object FlexibleLongSerializer : KSerializer<Long> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
		"FlexibleLong",
		PrimitiveKind.LONG
	)

	override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)

	override fun deserialize(decoder: Decoder): Long {
		val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeLong()
		return jsonDecoder.decodeJsonElement().jsonPrimitive.longOrNull
			?: throw SerializationException("Invalid long value")
	}
}

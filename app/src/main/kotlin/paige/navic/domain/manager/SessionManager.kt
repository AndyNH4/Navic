/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import dev.zt64.subsonic.api.model.Role
import dev.zt64.subsonic.api.model.User
import dev.zt64.subsonic.client.SubsonicAuth
import dev.zt64.subsonic.client.SubsonicClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.http
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import paige.navic.R
import paige.navic.util.getDefaultEngineForPlatform
import paige.navic.util.Logger
import paige.navic.util.extractProxyConfigFromUrl

class SessionManager(
	private val settings: Settings,
	private val preferenceManager: PreferenceManager,
	private val snackBarManager: SnackBarManager
) {

	val isLoggedIn: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val activeInstanceUrl: StateFlow<String>
		field = MutableStateFlow("")

	val isUsingBackupUrl: StateFlow<Boolean>
		field = MutableStateFlow(false)

	private var currentUser: User? = null
	private val mutex = Mutex()
	private val scope = CoroutineScope(Dispatchers.IO)

	var api: SubsonicClient = createClient(instanceUrl = activeUrl())
		private set

	init {
		activeInstanceUrl.value = activeUrl()
		isUsingBackupUrl.value = settings.getBoolean("usingBackupUrl", false)
		isLoggedIn.value = settings.getStringOrNull("username") != null
		if (isLoggedIn.value) {
			getCachedUser()
			scope.launch { checkAndFailover() }
		}
	}

	private fun activeUrl(): String {
		val primary = settings.getString("instanceUrl", "")
		if (!settings.getBoolean("usingBackupUrl", false)) return primary
		return settings.getString("instanceUrlBackup", "").ifBlank { primary }
	}

	fun backupUrl(): String =
		settings.getString("instanceUrlBackup", "")

	fun primaryUrl(): String =
		settings.getString("instanceUrl", "")

	fun setPrimaryUrl(url: String) {
		if (url.isBlank()) return
		settings["instanceUrl"] = url
		if (!settings.getBoolean("usingBackupUrl", false)) {
			refreshClient()
			activeInstanceUrl.value = activeUrl()
		}
	}

	private fun createClient(
		instanceUrl: String,
		username: String = settings.getString("username", ""),
		password: String = settings.getString("password", ""),
	) = SubsonicClient.Companion(
		baseUrl = instanceUrl,
		auth = SubsonicAuth.Token(
			username = username,
			password = password,
		),
		client = "Navic",
		clientConfig = {
			install(UserAgent) {
				agent = "Navic"
			}
			engine {
				proxy = extractProxyConfigFromUrl(preferenceManager.proxyUrl)
			}

			val customHeaders = preferenceManager.customHeadersMap()
			if (customHeaders.isNotEmpty()) {
				defaultRequest {
					customHeaders.forEach { (key, value) -> header(key, value) }
				}
			}
		},
		engine = getDefaultEngineForPlatform(preferenceManager.dangerousSslNoopEnabled)
	)

	suspend fun login(
		instanceUrl: String,
		username: String,
		password: String,
		backupUrl: String? = null
	) {
		var client = createClient(instanceUrl, username, password)
		var usingBackup = false

		try {
			client.ping()
			fetchCurrentUser(username, client)
		} catch (e: Exception) {
			val backup = backupUrl?.takeIf { it.isNotBlank() && it != instanceUrl }
				// TODO: custom exception instead of the generic "Exception"
				?: throw Exception(
					"Failed to connect to the instance. Please check your credentials and try again.",
					e
				)
			Logger.e("SessionManager", "Primary instance unreachable, trying backup", e)
			val backupClient = createClient(backup, username, password)
			try {
				backupClient.ping()
				fetchCurrentUser(username, backupClient)
			} catch (e2: Exception) {
				throw Exception(
					"Failed to connect to the instance. Please check your credentials and try again.",
					e2
				)
			}
			client = backupClient
			usingBackup = true
		}

		settings["instanceUrl"] = instanceUrl
		settings["instanceUrlBackup"] = backupUrl?.takeIf { it.isNotBlank() }
		settings["usingBackupUrl"] = usingBackup
		settings["username"] = username
		settings["password"] = password

		api = client
		activeInstanceUrl.value = if (usingBackup) backupUrl!! else instanceUrl
		isUsingBackupUrl.value = usingBackup
		isLoggedIn.value = true
	}

	fun logout() {
		settings["username"] = null
		settings["password"] = null
		isLoggedIn.value = false
		currentUser = null
	}

	fun refreshClient() {
		api = createClient(instanceUrl = activeUrl())
	}

	fun setBackupUrl(url: String) {
		settings["instanceUrlBackup"] = url.ifBlank { null }
		if (settings.getBoolean("usingBackupUrl", false)) {
			if (url.isBlank()) {
				settings["usingBackupUrl"] = false
				isUsingBackupUrl.value = false
			}
			refreshClient()
			activeInstanceUrl.value = activeUrl()
		}
	}

	suspend fun switchToOtherUrl(): Boolean {
		val other = if (isUsingBackupUrl.value) {
			settings.getString("instanceUrl", "")
		} else {
			settings.getString("instanceUrlBackup", "")
		}
		if (other.isBlank()) return false

		val reachable = try {
			withTimeout(8000) { createClient(other).ping() }
			true
		} catch (e: Exception) {
			Logger.e("SessionManager", "Other instance $other unreachable", e)
			false
		}
		if (!reachable) return false

		val nowUsingBackup = !isUsingBackupUrl.value
		settings["usingBackupUrl"] = nowUsingBackup
		isUsingBackupUrl.value = nowUsingBackup
		activeInstanceUrl.value = other
		refreshClient()
		notifySwitch(nowUsingBackup)
		return true
	}

	suspend fun checkAndFailover() {
		if (isLoggedIn.value.not()) return
		val reachable = try {
			withTimeout(8000) { api.ping() }
			true
		} catch (e: Exception) {
			Logger.e("SessionManager", "Active instance unreachable", e)
			false
		}
		if (reachable) return

		val hasAlternative = if (isUsingBackupUrl.value) {
			settings.getString("instanceUrl", "").isNotBlank()
		} else {
			settings.getString("instanceUrlBackup", "").isNotBlank()
		}
		if (hasAlternative) {
			switchToOtherUrl()
		}
	}

	private fun notifySwitch(nowUsingBackup: Boolean) {
		snackBarManager.notify(
			if (nowUsingBackup) R.string.notice_switched_to_backup
			else R.string.notice_switched_to_primary
		)
	}

	fun getCoverArtUrl(coverArtId: String) = api.getCoverArtUrl(
		coverArtId,
		auth = true,
		size = "${preferenceManager.coverArtQuality.value}"
	)


	private suspend fun fetchCurrentUser(
		username: String = settings.getString("username", ""),
		client: SubsonicClient = api
	): User? {
		mutex.withLock {
			if (username.isNotBlank()) {
				currentUser = client.getUser(username)
				return currentUser
			}
		}

		// TODO: custom exception instead of the generic "Exception"
		throw Exception("Failed to get current user because the username is blank")
	}

	fun getCachedUser(): User? {
		if (currentUser != null) {
			return currentUser
		}
		scope.launch {
			try {
				fetchCurrentUser()
			} catch (e: Exception) {
				Logger.e("SessionManager", "Failed to fetch current user info", e)
			}
		}
		return currentUser
	}
}

fun User.hasRole(role: Role): Boolean {
	return this.roles.contains(role)
}

fun User.canShare(): Boolean {
	return this.hasRole(Role.SHARE)
}

fun SessionManager.canUserShare(): Boolean {
	return this.getCachedUser()?.canShare() ?: false
}

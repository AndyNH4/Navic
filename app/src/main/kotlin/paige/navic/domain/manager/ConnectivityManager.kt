/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import android.annotation.SuppressLint
import android.content.Context
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import paige.navic.domain.model.settings.OfflineMode
import android.net.ConnectivityManager as AndroidConnectivityManager

private data class NetworkStatus(
	val isOnline: Boolean = false,
	val isCellular: Boolean = false
) {
	companion object {
		fun fromCaps(
			caps: NetworkCapabilities
		) = NetworkStatus(
			isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
				&& caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
			isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
				|| !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
		)
	}
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityManager(
	context: Context,
	private val preferenceManager: PreferenceManager,
	private val sessionManager: SessionManager
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val dispatcher = Dispatchers.IO
	private val started = SharingStarted.WhileSubscribed(5000)
	private val connectivityManager =
		context.getSystemService(Context.CONNECTIVITY_SERVICE) as AndroidConnectivityManager

	private val networkStatus = callbackFlow {
		val callback = object : AndroidConnectivityManager.NetworkCallback() {
			override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
				super.onCapabilitiesChanged(network, caps)
				trySend(NetworkStatus.fromCaps(caps))
			}

			override fun onLost(network: Network) {
				super.onLost(network)
				trySend(NetworkStatus())
			}
		}

		val request = NetworkRequest.Builder()
			.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
			.build()

		connectivityManager.registerNetworkCallback(request, callback)

		trySend(
			connectivityManager
				.getNetworkCapabilities(connectivityManager.activeNetwork)
				?.let { NetworkStatus.fromCaps(it) }
				?: NetworkStatus()
		)

		awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
	}
		.flowOn(dispatcher)
		.conflate()
		.stateIn(scope, started, NetworkStatus())

	// Server reachability: consecutive ping failures flip this to false so the
	// app falls back to offline mode; a single success restores it immediately.
	private val serverReachable = MutableStateFlow(true)
	private var consecutivePingFailures = 0

	init {
		scope.launch {
			// Re-check whenever the network comes up or changes transport.
			networkStatus.collect { status ->
				if (status.isOnline) {
					checkServerReachability()
				} else {
					serverReachable.value = false
					consecutivePingFailures = 0
				}
			}
		}
		scope.launch {
			// While unreachable, keep probing so the app recovers automatically.
			while (true) {
				delay(60_000)
				if (!serverReachable.value &&
					preferenceManager.offlineMode != OfflineMode.Forced
				) {
					checkServerReachability()
				}
			}
		}
	}

	suspend fun checkServerReachability(): Boolean {
		if (sessionManager.isLoggedIn.value.not()) return true.also { serverReachable.value = true }

		suspend fun ping(): Boolean = try {
			withTimeout(5000) { sessionManager.api.ping() }
			true
		} catch (_: Exception) {
			false
		}

		var reachable = ping()
		if (!reachable) {
			// May switch to the configured backup URL and rebuild the client.
			sessionManager.checkAndFailover()
			reachable = ping()
		}

		if (reachable) {
			consecutivePingFailures = 0
			serverReachable.value = true
		} else {
			consecutivePingFailures++
			if (consecutivePingFailures >= 2) {
				serverReachable.value = false
			}
		}
		return serverReachable.value
	}

	val isCellular = networkStatus
		.map { it.isCellular }
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, started, false)

	val isOnline = combine(networkStatus, serverReachable) { status, reachable ->
		when (preferenceManager.offlineMode) {
			OfflineMode.Forced -> false
			OfflineMode.NoWiFi -> status.isOnline && !status.isCellular && reachable
			else -> status.isOnline && reachable
		}
	}
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, started, true)
}

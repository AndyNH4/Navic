/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import paige.navic.R
import paige.navic.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Serializable
data class GitHubRelease(
	@SerialName("tag_name") val tag: String,
	@SerialName("html_url") val url: String,
	@SerialName("body") val body: String,
	@SerialName("assets") val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
	@SerialName("name") val name: String,
	@SerialName("browser_download_url") val downloadUrl: String
)

sealed interface UpdateDownloadState {
	data object Idle : UpdateDownloadState
	data class Downloading(val progress: Float) : UpdateDownloadState
	data object Failed : UpdateDownloadState
}

class UpdateManager(
	private val context: Context,
	private val snackBarManager: SnackBarManager
) {
	val release: StateFlow<GitHubRelease?>
		field = MutableStateFlow<GitHubRelease?>(null)

	val downloadState: StateFlow<UpdateDownloadState>
		field = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	private val updateClient = HttpClient {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
		install(HttpTimeout) {
			connectTimeoutMillis = 60_000
			socketTimeoutMillis = 120_000
			requestTimeoutMillis = 600_000
		}
	}

	private var checkJob: Job? = null
	private var downloadJob: Job? = null

	fun checkForUpdates(currentVersion: String, manual: Boolean = false) {
		if (checkJob?.isActive == true) {
			if (!manual) return
			checkJob?.cancel()
		}
		checkJob = scope.launch {
			try {
				val latestRelease: GitHubRelease =
					updateClient.get("https://api.github.com/repos/AndyNH4/Navic/releases/latest")
						.body()
				val remoteVersion = latestRelease.tag
					.filter { it.isDigit() }
					.toIntOrNull()
				val localVersion = currentVersion
					.filter { it.isDigit() }
					.toIntOrNull()
				if (remoteVersion == null || localVersion == null) {
					if (manual) {
						snackBarManager.notify(R.string.notice_update_check_failed)
					}
					return@launch
				}
				if (remoteVersion > localVersion || "$remoteVersion".length != "$localVersion".length) {
					release.value = latestRelease
				} else if (manual) {
					snackBarManager.notify(R.string.notice_no_updates_available)
				}
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Logger.e("UpdateManager", "couldn't check for updates", e)
				if (manual) {
					snackBarManager.notify(R.string.notice_update_check_failed)
				}
			}
		}
	}

	fun clearRelease() {
		release.value = null
	}

	fun downloadAndInstall(release: GitHubRelease) {
		if (downloadJob?.isActive == true) return

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
			&& !context.packageManager.canRequestPackageInstalls()
		) {
			val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
				data = "package:${context.packageName}".toUri()
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			}
			context.startActivity(intent)
			snackBarManager.notify(R.string.notice_install_permission_required)
			return
		}

		val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
		if (asset == null) {
			Logger.e("UpdateManager", "no apk asset found in release ${release.tag}")
			downloadState.value = UpdateDownloadState.Failed
			return
		}

		downloadJob = scope.launch {
			val dir = File(context.cacheDir, "updates").apply { mkdirs() }
			val file = File(dir, UPDATE_FILE_NAME)
			try {
				downloadState.value = UpdateDownloadState.Downloading(0f)
				withContext(Dispatchers.IO) {
					updateClient.prepareRequest(asset.downloadUrl) {
						method = HttpMethod.Get
						onDownload { bytesSentTotal, contentLength ->
							val progress = if (contentLength != null && contentLength > 0L) {
								bytesSentTotal.toFloat() / contentLength
							} else {
								-1f
							}
							downloadState.value = UpdateDownloadState.Downloading(progress)
						}
					}.execute { response ->
						if (response.status.value !in 200..299) {
							throw IOException("update download failed with HTTP ${response.status.value}")
						}
						FileOutputStream(file).use { outputStream ->
							response.bodyAsChannel().copyTo(outputStream)
						}
					}
				}
				if (file.length() <= 0L) throw IOException("downloaded update is empty")
				downloadState.value = UpdateDownloadState.Idle
				installApk(file)
			} catch (e: Exception) {
				file.delete()
				if (e is CancellationException) throw e
				Logger.e("UpdateManager", "failed to download update", e)
				downloadState.value = UpdateDownloadState.Failed
			}
		}
	}

	fun cancelDownload() {
		downloadJob?.cancel()
		downloadJob = null
		downloadState.value = UpdateDownloadState.Idle
	}

	private fun installApk(file: File) {
		val uri = FileProvider.getUriForFile(
			context,
			"${context.packageName}.fileprovider",
			file
		)
		val intent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(uri, "application/vnd.android.package-archive")
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		context.startActivity(intent)
	}

	companion object {
		private const val UPDATE_FILE_NAME = "rezeener-update.apk"
	}
}

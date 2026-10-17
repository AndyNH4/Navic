/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.entity.DownloadEntity
import paige.navic.data.database.entity.DownloadStatus
import paige.navic.util.Logger
import java.io.File

class DownloadRecoveryManager(
	private val context: Context,
	private val downloadDao: DownloadDao
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	fun recoverOrphanedDownloads() {
		scope.launch {
			try {
				val downloadDir = File(context.filesDir, "downloads")
				val files = downloadDir.listFiles()
					?.filter { it.isFile && it.length() > 0L }
					.orEmpty()
				if (files.isEmpty()) return@launch
				if (downloadDao.getAllDownloadsList().isNotEmpty()) return@launch

				var recoveredCount = 0
				files.forEach { file ->
					val songId = file.nameWithoutExtension
					if (file.extension.isNotEmpty() && songId.isNotBlank() &&
						downloadDao.getDownloadById(songId) == null
					) {
						downloadDao.insertDownload(
							DownloadEntity(
								songId = songId,
								status = DownloadStatus.DOWNLOADED,
								progress = 1f,
								filePath = file.absolutePath
							)
						)
						recoveredCount++
					}
				}
				Logger.i(
					"DownloadRecoveryManager",
					"Recovered $recoveredCount orphaned download records"
				)
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Logger.e("DownloadRecoveryManager", "Failed to recover orphaned downloads", e)
			}
		}
	}
}

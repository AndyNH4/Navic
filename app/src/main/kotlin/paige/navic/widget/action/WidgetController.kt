/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.action

import android.app.Application
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import androidx.media3.session.MediaController
import paige.navic.exoplayer.ExoStateHolder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WidgetController(
	private val application: Application
) {
	suspend fun <T> withController(
		block: suspend (MediaController) -> T
	): T = withContext(Dispatchers.Main.immediate) {
		val future = MediaController.Builder(
			application,
			ExoStateHolder.newSessionToken(application)
		).buildAsync()
		try {
			block(future.await())
		} finally {
			MediaController.releaseFuture(future)
		}
	}
}

internal suspend fun <T> ListenableFuture<T>.await(): T =
	suspendCancellableCoroutine { continuation ->
		addListener({
			try {
				continuation.resume(get())
			} catch (error: Throwable) {
				continuation.resumeWithException(error.cause ?: error)
			}
		}, MoreExecutors.directExecutor())
		continuation.invokeOnCancellation { cancel(false) }
	}

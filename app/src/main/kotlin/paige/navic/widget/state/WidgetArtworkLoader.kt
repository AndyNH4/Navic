/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.state

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import paige.navic.domain.manager.PreferenceManager

class WidgetArtworkLoader(
	private val context: Context,
	private val imageLoader: ImageLoader,
	private val preferenceManager: PreferenceManager
) {
	private val bitmaps = LruCache<String, Bitmap>(12)

	suspend fun load(uri: String?, sizePx: Int): Bitmap? {
		if (uri.isNullOrBlank() || sizePx <= 0) return null
		val artworkKey = uri.toUri().getQueryParameter("id") ?: uri
		val cacheKey = "$artworkKey@$sizePx"
		bitmaps.get(cacheKey)?.let { return it }
		if (sizePx < LARGE_SIZE) {
			bitmaps.get("$artworkKey@$LARGE_SIZE")?.let { large ->
				return Bitmap.createScaledBitmap(large, sizePx, sizePx, true).also {
					bitmaps.put(cacheKey, it)
				}
			}
		}

		val headers = NetworkHeaders.Builder().apply {
			preferenceManager.customHeadersMap().forEach { (key, value) -> add(key, value) }
		}.build()
		val request = ImageRequest.Builder(context)
			.data(uri)
			.size(Size(sizePx, sizePx))
			.allowHardware(false)
			.memoryCacheKey("$artworkKey:widget:$sizePx")
			.diskCacheKey(artworkKey)
			.memoryCachePolicy(CachePolicy.ENABLED)
			.diskCachePolicy(CachePolicy.ENABLED)
			.httpHeaders(headers)
			.build()
		val bitmap = (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
		if (bitmap != null) bitmaps.put(cacheKey, bitmap)
		return bitmap
	}

	suspend fun prefetch(uri: String?) {
		load(uri, LARGE_SIZE)
		load(uri, SMALL_SIZE)
	}

	companion object {
		const val SMALL_SIZE = 128
		const val LARGE_SIZE = 512
	}
}

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import java.io.File
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Immutable
data class CachedImageItem(
	val file: File,
	val sizeBytes: Long
)

class SettingsImageCacheViewModel(
	private val imageLoader: ImageLoader
) : ViewModel() {

	val items: StateFlow<ImmutableList<CachedImageItem>?>
		field = MutableStateFlow(null)

	init {
		viewModelScope.launch(Dispatchers.IO) {
			items.value = enumerateCachedImages()
		}
	}

	fun delete(item: CachedImageItem) {
		viewModelScope.launch(Dispatchers.IO) {
			val baseName = item.file.name.removeSuffix(DATA_SUFFIX)
			item.file.delete()
			item.file.parentFile?.resolve(baseName + METADATA_SUFFIX)?.delete()
			items.value = enumerateCachedImages()
		}
	}

	fun clearAll() {
		viewModelScope.launch(Dispatchers.IO) {
			imageLoader.memoryCache?.clear()
			imageLoader.diskCache?.clear()
			items.value = enumerateCachedImages()
		}
	}

	private fun enumerateCachedImages(): ImmutableList<CachedImageItem> {
		val directory = imageLoader.diskCache?.directory?.toFile() ?: return persistentListOf()
		return directory.listFiles()
			.orEmpty()
			.filter { it.isFile && it.name.endsWith(DATA_SUFFIX) }
			.map { CachedImageItem(file = it, sizeBytes = it.length()) }
			.sortedByDescending { it.file.lastModified() }
			.toImmutableList()
	}

	private companion object {
		// Coil's disk cache stores each entry as <hash>.0 (metadata) and
		// <hash>.1 (raw image bytes); journal files don't match this suffix
		const val DATA_SUFFIX = ".1"
		const val METADATA_SUFFIX = ".0"
	}
}

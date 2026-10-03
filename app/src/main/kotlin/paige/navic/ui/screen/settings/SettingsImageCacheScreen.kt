/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.R
import paige.navic.di.LocalSizeClass
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Error
import paige.navic.icons.outlined.MoreVert
import paige.navic.ui.component.common.ContentUnavailable
import paige.navic.ui.component.common.TooltipBox
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.component.layout.NestedTopBarDefaults
import paige.navic.ui.screen.settings.viewmodel.CachedImageItem
import paige.navic.ui.screen.settings.viewmodel.SettingsImageCacheViewModel

@Composable
fun SettingsImageCacheScreen() {
	val viewModel = koinViewModel<SettingsImageCacheViewModel>()

	val sizeClass = LocalSizeClass.current
	val hideBack = sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

	val items by viewModel.items.collectAsStateWithLifecycle()
	val imageLoader = koinInject<ImageLoader>()

	var menuExpanded by remember { mutableStateOf(false) }
	var pendingDelete by remember { mutableStateOf<CachedImageItem?>(null) }
	var previewItem by remember { mutableStateOf<CachedImageItem?>(null) }

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(R.string.title_image_cache)) },
				navigationAction = {
					if (!hideBack) {
						NestedTopBarDefaults.NavigationAction()
					}
				},
				actions = {
					Box {
						TooltipBox(stringResource(R.string.action_clear_image_cache)) {
							IconButton(onClick = { menuExpanded = true }) {
								Icon(
									imageVector = Icons.Outlined.MoreVert,
									contentDescription = stringResource(R.string.action_clear_image_cache)
								)
							}
						}
						DropdownMenu(
							expanded = menuExpanded,
							onDismissRequest = { menuExpanded = false }
						) {
							DropdownMenuItem(
								text = { Text(stringResource(R.string.action_clear_image_cache)) },
								onClick = {
									menuExpanded = false
									viewModel.clearAll()
								}
							)
						}
					}
				}
			)
		}
	) { innerPadding ->
		val currentItems = items
		when {
			currentItems == null -> {
				Box(
					modifier = Modifier.fillMaxSize().padding(innerPadding),
					contentAlignment = Alignment.Center
				) {
					CircularProgressIndicator()
				}
			}

			currentItems.isEmpty() -> {
				ContentUnavailable(
					label = stringResource(R.string.info_no_cached_images),
					modifier = Modifier.padding(innerPadding)
				)
			}

			else -> {
				val totalSize = remember(currentItems) { currentItems.sumOf { it.sizeBytes } }
				val summaryText = pluralStringResource(
					R.plurals.count_images,
					currentItems.size,
					currentItems.size
				) + " • " + formatFileSize(totalSize)

				LazyVerticalGrid(
					modifier = Modifier.fillMaxSize().padding(innerPadding),
					columns = GridCells.Fixed(3),
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
						Text(
							text = summaryText,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(vertical = 8.dp)
						)
					}
					items(
						items = currentItems,
						key = { it.file.name }
					) { item ->
						ImageCacheGridItem(
							item = item,
							imageLoader = imageLoader,
							onClick = { previewItem = item },
							onLongClick = { pendingDelete = item }
						)
					}
				}
			}
		}
	}

	pendingDelete?.let { item ->
		AlertDialog(
			title = { Text(stringResource(R.string.title_delete_cached_image)) },
			text = { Text(stringResource(R.string.info_delete_cached_image)) },
			onDismissRequest = { pendingDelete = null },
			confirmButton = {
				Button(onClick = {
					viewModel.delete(item)
					pendingDelete = null
				}) {
					Text(stringResource(R.string.action_delete))
				}
			},
			dismissButton = {
				TextButton(onClick = { pendingDelete = null }) {
					Text(stringResource(R.string.action_cancel))
				}
			}
		)
	}

	previewItem?.let { item ->
		Dialog(
			onDismissRequest = { previewItem = null },
			properties = DialogProperties(usePlatformDefaultWidth = false)
		) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(Color.Black)
					.clickable { previewItem = null },
				contentAlignment = Alignment.Center
			) {
				SubcomposeAsyncImage(
					model = item.file,
					imageLoader = imageLoader,
					contentDescription = null,
					modifier = Modifier.fillMaxSize(),
					contentScale = ContentScale.Fit
				)
			}
		}
	}
}

@Composable
private fun ImageCacheGridItem(
	item: CachedImageItem,
	imageLoader: ImageLoader,
	onClick: () -> Unit,
	onLongClick: () -> Unit
) {
	Column(
		modifier = Modifier
			.clip(MaterialTheme.shapes.medium)
			.combinedClickable(onClick = onClick, onLongClick = onLongClick)
	) {
		SubcomposeAsyncImage(
			model = item.file,
			imageLoader = imageLoader,
			contentDescription = null,
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(1f)
				.background(MaterialTheme.colorScheme.surfaceVariant),
			contentScale = ContentScale.Crop,
			error = {
				Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center
				) {
					Icon(Icons.Outlined.Error, null)
				}
			}
		)
		Text(
			text = formatFileSize(item.sizeBytes),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
		)
	}
}

private fun formatFileSize(bytes: Long): String {
	val mb = bytes.toDouble() / (1024 * 1024)
	return if (mb >= 1024) {
		val gb = mb / 1024
		"${(gb * 100).toInt() / 100.0} GB"
	} else if (mb >= 1) {
		"${(mb * 10).toInt() / 10.0} MB"
	} else {
		"${bytes / 1024} KB"
	}
}

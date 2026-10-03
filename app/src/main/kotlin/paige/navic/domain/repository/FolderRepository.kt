/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import paige.navic.data.database.mapper.toDomainModel
import paige.navic.data.database.mapper.toEntity
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.model.DomainFolder
import paige.navic.domain.model.DomainFolderContent
import dev.zt64.subsonic.api.model.Album as ApiAlbum
import dev.zt64.subsonic.api.model.Song as ApiSong

class FolderRepository(
	private val sessionManager: SessionManager
) {
	suspend fun getMusicFolders(): ImmutableList<DomainFolder> = withContext(Dispatchers.IO) {
		sessionManager.api.getDirectories()
			.map { DomainFolder(id = it.id.toString(), name = it.name, isMusicFolder = true) }
			.toImmutableList()
	}

	suspend fun getContent(folder: DomainFolder): DomainFolderContent = withContext(Dispatchers.IO) {
		if (folder.isMusicFolder) {
			getMusicFolderContent(folder)
		} else {
			getDirectoryContent(folder)
		}
	}

	private suspend fun getMusicFolderContent(folder: DomainFolder): DomainFolderContent {
		// the root of a music folder has no directory id, so its top-level
		// directories are fetched through the artist index instead
		val indexes = sessionManager.api.getIndexes(musicFolderId = folder.id)
		val artists = indexes.index.flatMap { it.artists } + indexes.shortcut
		return DomainFolderContent(
			folders = artists
				.distinctBy { it.id }
				.map { DomainFolder(id = it.id, name = it.name) }
				.toImmutableList(),
			songs = persistentListOf()
		)
	}

	private suspend fun getDirectoryContent(folder: DomainFolder): DomainFolderContent {
		val directory = sessionManager.api.getDirectory(folder.id)
		// child directories are reported by the server with mediaType "album"
		return DomainFolderContent(
			folders = directory.items
				.filterIsInstance<ApiAlbum>()
				.map { DomainFolder(id = it.id, name = it.name ?: it.artistName ?: it.id) }
				.toImmutableList(),
			songs = directory.items
				.filterIsInstance<ApiSong>()
				.map { it.toEntity().toDomainModel() }
				.toImmutableList()
		)
	}
}

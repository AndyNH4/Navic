/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import paige.navic.domain.manager.AppIconManager
import paige.navic.domain.manager.ArtistGroupManager
import paige.navic.domain.manager.AudioGainManager
import paige.navic.domain.manager.CastManager
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.DownloadRecoveryManager
import paige.navic.domain.manager.EqualizerManager
import paige.navic.domain.manager.LinkManager
import paige.navic.domain.manager.LogManager
import paige.navic.domain.manager.LoginManager
import paige.navic.domain.manager.NotificationManager
import paige.navic.domain.manager.PermissionManager
import paige.navic.domain.manager.PlayHistoryManager
import paige.navic.domain.manager.PlayHistorySyncManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.ShareManager
import paige.navic.domain.manager.SleepTimerManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.manager.StorageManager
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.manager.UpdateManager
import paige.navic.domain.manager.VoiceActionManager
import paige.navic.exoplayer.ExoStateHolder

@OptIn(UnstableApi::class)
val managerModule = module {
	singleOf(::AppIconManager)
	singleOf(::ArtistGroupManager)
	singleOf(::AudioGainManager)
	singleOf(::ConnectivityManager)
	singleOf(::DownloadManager)
	singleOf(::EqualizerManager)
	singleOf(::LinkManager)
	singleOf(::LogManager)
	singleOf(::LoginManager)
	singleOf(::NotificationManager)
	singleOf(::PermissionManager)
	singleOf(::PlayHistoryManager)
	singleOf(::PlayHistorySyncManager)
	singleOf(::PreferenceManager)
	singleOf(::SessionManager)
	singleOf(::ShareManager)
	singleOf(::SleepTimerManager)
	singleOf(::SnackBarManager)
	singleOf(::StorageManager)
	singleOf(::VoiceActionManager)
	single(createdAtStart = true) {
		SyncManager(get(), get(), get(), get(), get(), get(), get(), get()).apply {
			startPeriodicSync()
		}
	}
	single(createdAtStart = true) {
		CastManager(get(), get(), get()).apply {
			initialize()
		}
	}
	singleOf(::ExoStateHolder)
}

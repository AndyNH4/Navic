/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import paige.navic.domain.manager.WidgetNavigationManager
import paige.navic.widget.action.WidgetActionHandler
import paige.navic.widget.action.WidgetController
import paige.navic.widget.quickplay.QuickPlayActionHandler
import paige.navic.widget.quickplay.QuickPlayRepository
import paige.navic.widget.state.WidgetArtworkLoader
import paige.navic.widget.state.WidgetSnapshotStore
import paige.navic.widget.state.WidgetStatePublisher
import paige.navic.widget.state.WidgetUpdateDispatcher

val widgetDataStoreQualifier = named("widget_snapshot")

val widgetModule = module {
	single<DataStore<Preferences>>(widgetDataStoreQualifier) {
		createDataStore(
			producePath = {
				androidContext().filesDir.resolve("widget_snapshot.preferences_pb").toOkioPath()
			}
		)
	}
	single { WidgetSnapshotStore(get(widgetDataStoreQualifier)) }
	single { WidgetController(androidApplication()) }
	singleOf(::WidgetActionHandler)
	singleOf(::WidgetArtworkLoader)
	singleOf(::WidgetStatePublisher)
	singleOf(::WidgetUpdateDispatcher)
	singleOf(::WidgetNavigationManager)
	singleOf(::QuickPlayActionHandler)
	singleOf(::QuickPlayRepository)
}

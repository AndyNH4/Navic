/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.exoplayer.cast

import android.content.Context
import androidx.media3.cast.DefaultCastOptionsProvider
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Manifest-registered Cast options targeting the default media receiver,
 * mirroring media3's [DefaultCastOptionsProvider]. The media3 Cast singleton
 * normally initializes with its own defaults; this provider covers Cast SDK
 * code paths that read options from the manifest.
 */
@UnstableApi
class NavicCastOptionsProvider : OptionsProvider {
	override fun getCastOptions(context: Context): CastOptions =
		CastOptions.Builder()
			.setReceiverApplicationId(DefaultCastOptionsProvider.APP_ID_DEFAULT_RECEIVER_WITH_DRM)
			.setResumeSavedSession(false)
			.setEnableReconnectionService(false)
			.setStopReceiverApplicationWhenEndingSession(true)
			.setRemoteToLocalEnabled(true)
			.build()

	override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> =
		emptyList()
}

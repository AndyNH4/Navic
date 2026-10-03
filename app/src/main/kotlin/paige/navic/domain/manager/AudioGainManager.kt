/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager

import androidx.media3.common.util.UnstableApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import paige.navic.domain.model.DomainReplayGain
import paige.navic.domain.model.settings.ReplayGainMode
import paige.navic.exoplayer.ExoStateHolder
import paige.navic.exoplayer.impl.ExoAudioGainProcessor

@UnstableApi
class AudioGainManager: KoinComponent {
	private val stateHolder: ExoStateHolder by inject()

	// the crossfade engine runs one gain processor per leg; writes go to both
	private fun forEachProcessor(block: (ExoAudioGainProcessor) -> Unit) {
		block(stateHolder.gainProcessor)
		block(stateHolder.gainProcessorB)
	}

    fun applyGainMode(
        mode: ReplayGainMode
    ) {
		forEachProcessor { it.applyGainMode(mode) }
    }

	fun resetGain() {
		forEachProcessor { it.resetGain() }
	}

	fun setAmplifierValues(withReplayGain: Float, withoutReplayGain: Float) {
		forEachProcessor {
			it.rgAmpValue = withReplayGain
			it.ampValue = withoutReplayGain
		}
	}

	fun setReplayGainMetadata(metadata: DomainReplayGain?) {
		forEachProcessor { it.setReplayGainMetadata(metadata) }
	}
}

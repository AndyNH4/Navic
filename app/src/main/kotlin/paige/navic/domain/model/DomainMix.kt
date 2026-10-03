/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model

import androidx.compose.runtime.Immutable

enum class DomainMixKind {
	RightNow,
	Genre,
	TimeMachine,
	MixItUp,
	DeepCuts,
	FavoritesRadio,
	GenreBlend,
	HeavyRotation
}

sealed interface DomainMixStrategy {
	data class RandomByGenre(val genre: String, val size: Int) : DomainMixStrategy
	data class RandomByYearRange(val fromYear: Int, val toYear: Int, val size: Int) : DomainMixStrategy
	data class SimilarToArtist(val artistId: String, val count: Int) : DomainMixStrategy
	data class SimilarToSong(val songId: String, val count: Int) : DomainMixStrategy
	data class GenreBlend(val genres: List<Pair<String, Int>>) : DomainMixStrategy
	data class Random(val size: Int) : DomainMixStrategy
	data class RecentTopSongs(val songIds: List<String>) : DomainMixStrategy
}

@Immutable
data class DomainMix(
	val id: String,
	val kind: DomainMixKind,
	/** Primary format arg for the card title (genre name, decade label, ...) */
	val titleArg: String? = null,
	/** Primary format arg for the card subtitle (genre, artist or song name, ...) */
	val subtitleArg: String? = null,
	val coverArtIds: List<String> = emptyList(),
	val strategy: DomainMixStrategy
)

/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.util

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import paige.navic.R
import paige.navic.di.LocalSnackBarState
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.DomainSongArtist
import paige.navic.icons.Icons
import paige.navic.icons.filled.Explicit
import paige.navic.ui.theme.warning

val InlineExplicitIcon = persistentMapOf(
	"InlineExplicitIcon" to InlineTextContent(
		Placeholder(
			width = 16.sp,
			height = 16.sp,
			placeholderVerticalAlign = PlaceholderVerticalAlign.Center
		)
	) {
		Icon(
			imageVector = Icons.Filled.Explicit,
			contentDescription = stringResource(R.string.info_explicit)
		)
	}
)

val InlineExplicitIconLarge = persistentMapOf(
	"InlineExplicitIcon" to InlineTextContent(
		Placeholder(
			width = 20.sp,
			height = 20.sp,
			placeholderVerticalAlign = PlaceholderVerticalAlign.Center
		)
	) {
		Icon(
			imageVector = Icons.Filled.Explicit,
			contentDescription = stringResource(R.string.info_explicit)
		)
	}
)

fun AnnotatedString.Builder.appendBulletPoint()
	= append(" • ")

fun DomainSong.displayArtistName(): String? {
	val structuredName = artists
		.map { it.name.trim() }
		.filter { it.isNotEmpty() }
		.joinToString(", ")
	return structuredName
		.ifBlank { artistName?.trim().orEmpty() }
		.takeIf { it.isNotEmpty() }
}

fun AnnotatedString.Builder.appendArtists(
	artists: List<DomainSongArtist>,
	fallbackName: String? = null,
	fallbackArtistId: String? = null,
	onClick: (artistId: String) -> Unit
) {
	val listener = LinkInteractionListener { annotation ->
		val artistId = (annotation as LinkAnnotation.Clickable).tag
		onClick(artistId)
	}
	val displayArtists = artists.filter { it.name.isNotBlank() }.ifEmpty {
		fallbackName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
			listOf(DomainSongArtist(id = fallbackArtistId.orEmpty(), name = name))
		}.orEmpty()
	}
	displayArtists.forEachIndexed { index, artist ->
		val artistId = artist.id.trim()
		if (artistId.isNotEmpty() && artistId != "unknown artist") {
			withLink(
				link = LinkAnnotation.Clickable(
					linkInteractionListener = listener,
					tag = artistId,
					styles = TextLinkStyles()
				)
			) {
				append(artist.name)
			}
		} else {
			append(artist.name)
		}
		if (index != displayArtists.lastIndex) {
			append(", ")
		}
	}
}

@Composable
fun buildSongInfoString(
	song: DomainSong,
	onClickArtist: (artistId: String) -> Unit,
	showExternal: Boolean = true,
	showAlbum: Boolean = true,
	showYear: Boolean = true,
	showPlayCount: Boolean = false
): AnnotatedString {
	val snackBarState = LocalSnackBarState.current
	val extSnackBarText = stringResource(R.string.info_external_song_description)
	val scope = rememberCoroutineScope()

	return buildAnnotatedString {
		if ((song.isExternal || song.id.startsWith("ext-")) && showExternal) {
			withLink(
				link = LinkAnnotation.Clickable(
					linkInteractionListener = LinkInteractionListener {
						scope.launch {
							snackBarState.currentSnackbarData?.dismiss()
							snackBarState.showSnackbar(extSnackBarText)
						}
					},
					tag = "external",
					styles = TextLinkStyles(
						style = SpanStyle(
							color = MaterialTheme.colorScheme.warning,
							textDecoration = TextDecoration.Underline
						)
					)
				)
			) {
				append(stringResource(R.string.info_external_song))
			}
			appendBulletPoint()
		}

		if (showAlbum) {
			append("${song.albumTitle}")
			appendBulletPoint()
		}

		if (showYear && song.year != null) {
			append("${song.year}")
			appendBulletPoint()
		}

		appendArtists(
			artists = song.artists,
			fallbackName = song.artistName,
			fallbackArtistId = song.artistId,
			onClick = onClickArtist
		)

		if (showPlayCount && song.playCount > 0) {
			appendBulletPoint()
			append(pluralStringResource(R.plurals.count_plays, song.playCount, song.playCount))
		}
	}
}

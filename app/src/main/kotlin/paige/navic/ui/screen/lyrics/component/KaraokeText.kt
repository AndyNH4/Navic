/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.lyrics.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.lyrics.LyricsWord
import kotlin.time.Duration

private const val ACTIVE_WORD_SCALE = 1.18f

@Composable
fun LyricsScreenKaraokeText(
	text: String,
	progress: Float,
	isActive: Boolean,
	isSynced: Boolean,
	isBeatByBeat: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	words: List<LyricsWord>? = null,
	currentDuration: Duration? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()

	var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

	val smoothProgress by animateFloatAsState(
		targetValue = progress,
		animationSpec = spring(stiffness = Spring.StiffnessLow, visibilityThreshold = 0.001f)
	)

	val lyricsBeatByBeat = preferenceManager.lyricsBeatByBeat
	val lyricsBrightInactive = preferenceManager.lyricsBrightInactive
	val lyricsWordScale = preferenceManager.lyricsWordScale

	val baseFontSize = 32.sp

	// word-level timing available: enlarge the word currently being sung on top of
	// the regular gradient wipe. gated by beat-by-beat since it is part of that system
	val wordMode = lyricsBeatByBeat && lyricsWordScale && isActive
		&& !words.isNullOrEmpty() && currentDuration != null

	val currentWordIndex = if (wordMode) {
		words!!.indexOfLast { currentDuration!! >= it.time }
	} else -1

	val wordScales = remember(words) {
		words?.map { Animatable(1f) }.orEmpty()
	}

	LaunchedEffect(currentWordIndex, wordMode) {
		wordScales.forEachIndexed { index, animatable ->
			launch {
				animatable.animateTo(
					targetValue = if (wordMode && index == currentWordIndex) ACTIVE_WORD_SCALE else 1f,
					animationSpec = spring(
						dampingRatio = Spring.DampingRatioMediumBouncy,
						stiffness = Spring.StiffnessMediumLow
					)
				)
			}
		}
	}

	// locate words inside the original text the same way calculateWordProgress does,
	// so the rendered string never changes, only spans are layered on top
	val displayText = if (wordMode && currentWordIndex >= 0 && words != null) {
		buildAnnotatedString {
			append(text)
			var searchFrom = 0
			words.forEachIndexed { index, word ->
				if (word.text.isEmpty()) return@forEachIndexed
				val wordIndex = text.indexOf(word.text, startIndex = searchFrom, ignoreCase = true)
				if (wordIndex < 0) return@forEachIndexed
				searchFrom = wordIndex + word.text.length
				val scale = wordScales.getOrNull(index)?.value ?: 1f
				if (scale > 1.001f) {
					addStyle(
						SpanStyle(
							fontSize = baseFontSize * scale,
							fontWeight = FontWeight.ExtraBold
						),
						start = wordIndex,
						end = wordIndex + word.text.length
					)
				}
			}
		}
	} else AnnotatedString(text)

	val isRtl = textLayoutResult?.let { layout ->
		(0 until layout.lineCount).any { lineIndex ->
			layout.getBidiRunDirection(layout.getLineStart(lineIndex)) == ResolvedTextDirection.Rtl
		}
	} ?: false

	val inactiveAlpha = when {
		!isSynced -> 1f
		lyricsBrightInactive -> 0.9f
		else -> 0.35f
	}

	val alphaTransition by animateFloatAsState(
		targetValue = if (isActive) 1f else inactiveAlpha,
		animationSpec = spring(stiffness = Spring.StiffnessLow)
	)

	Box(modifier = modifier.clickable { onClick() }) {
		Text(
			text = displayText,
			fontSize = baseFontSize,
			fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
			textAlign = if (isRtl) TextAlign.End else TextAlign.Start,
			style = MaterialTheme.typography.headlineLargeEmphasized,
			color = if (lyricsBrightInactive) Color.White.copy(alpha = alphaTransition * 0.4f)
			else MaterialTheme.colorScheme.onSurface.copy(alpha = alphaTransition * 0.4f),
			onTextLayout = { textLayoutResult = it },
			modifier = Modifier.fillMaxWidth()
		)

		if (isActive) {
			Text(
				text = displayText,
				fontSize = baseFontSize,
				fontWeight = FontWeight.Bold,
				textAlign = if (isRtl) TextAlign.End else TextAlign.Start,
				style = MaterialTheme.typography.headlineLargeEmphasized,
				color = Color.White,
				modifier = Modifier
					.fillMaxWidth()
					.alpha(alphaTransition)
					.then(
						if (lyricsBeatByBeat && isBeatByBeat) {
							Modifier
								.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
								.drawWithCache {
									onDrawWithContent {
										val layout = textLayoutResult ?: return@onDrawWithContent
										drawContent()

										val totalWidth = (0 until layout.lineCount).sumOf {
											(layout.getLineRight(it) - layout.getLineLeft(it)).toDouble()
										}.toFloat()

										val feather = 50f
										val adjustedTotalWidth = totalWidth + (feather * 2)
										val currentPixelTarget =
											(adjustedTotalWidth * smoothProgress) - feather

										var accumulatedWidth = 0f

										for (i in 0 until layout.lineCount) {
											val lineLeft = layout.getLineLeft(i)
											val lineRight = layout.getLineRight(i)
											val lineWidth = lineRight - lineLeft
											if (lineWidth <= 0f) continue

											val lineTop = layout.getLineTop(i)
											val lineBottom = layout.getLineBottom(i)
											val isRtl =
												layout.getBidiRunDirection(layout.getLineStart(i)) == ResolvedTextDirection.Rtl

											val startOffFadeIn =
												currentPixelTarget - accumulatedWidth - feather
											val endOfFadeIn =
												currentPixelTarget - accumulatedWidth + feather

											val startX = if (isRtl) lineRight else lineLeft
											val endX = if (isRtl) lineLeft else lineRight

											val brush = Brush.linearGradient(
												0.0f to Color.White,
												(startOffFadeIn / lineWidth).coerceIn(
													0f,
													1f
												) to Color.White,
												(endOfFadeIn / lineWidth).coerceIn(
													0f,
													1f
												) to Color.Transparent,
												1.0f to Color.Transparent,
												start = Offset(startX, 0f),
												end = Offset(endX, 0f)
											)

											drawRect(
												brush = brush,
												topLeft = Offset(lineLeft, lineTop),
												size = Size(lineWidth, lineBottom - lineTop),
												blendMode = BlendMode.SrcIn
											)
											accumulatedWidth += lineWidth
										}
									}
								}
						} else Modifier
					)
			)
		}
	}
}

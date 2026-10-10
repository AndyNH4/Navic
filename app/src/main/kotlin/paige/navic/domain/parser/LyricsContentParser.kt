/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.parser

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import paige.navic.domain.model.lyrics.LyricsLine
import paige.navic.domain.model.lyrics.LyricsWord
import paige.navic.domain.model.lyrics.ParsedLyrics
import paige.navic.util.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class YoulyResponse(
	val lyrics: List<YoulyLine> = emptyList()
)

@Serializable
private data class YoulyLine(
	val time: Long = 0L,
	val text: String = "",
	val syllabus: List<YoulySyllable>? = null
)

@Serializable
private data class YoulySyllable(
	val time: Long = 0L,
	val duration: Long = 0L,
	val text: String = ""
)

object LyricsContentParser {
	private val jsonParser = Json {
		isLenient = true
		explicitNulls = false
		ignoreUnknownKeys = true
	}

	// leading line timestamp: [mm:ss], [mm:ss.xx] or [mm:ss.xxx]
	private val LINE_TIMESTAMP = Regex("""^\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

	// inline word timestamps, both [mm:ss.xxx] (square) and <mm:ss.xx> (standard ELRC)
	private val WORD_TAG_SQUARE = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
	private val WORD_TAG_ANGLE = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

	// translation marker: a line sharing the original's timestamp whose text starts with [tr]
	private const val TR_PREFIX = "[tr]"

	// optional file header declaring the translation language: [tr-lang:xx]
	private val TR_LANG = Regex("""^\[tr-lang:\s*([^\]]*?)\s*]$""", RegexOption.IGNORE_CASE)

	private val MIN_WORD_DURATION = 1.milliseconds
	private val FALLBACK_LINE_DURATION = 3.seconds

	private data class RawLine(
		val time: Duration?,
		val text: String,
		val words: List<LyricsWord>?,
		val isTranslation: Boolean,
		val danglingWordIndex: Int = -1
	)

	fun parse(content: String): ParsedLyrics? {
		val text = content.trim()
		if (text.isEmpty()) return null

		return try {
			if (text.startsWith("{")) {
				parseJson(text)
			} else {
				parseLrc(text)
			}
		} catch (e: Exception) {
			Logger.e("LyricRepository", "Lyrics parsing failed!", e)
			null
		}
	}

	private fun parseJson(jsonString: String): ParsedLyrics? {
		val jsonObject = jsonParser.parseToJsonElement(jsonString).jsonObject

		val syncedStr = jsonObject["syncedLyrics"]?.jsonPrimitive?.contentOrNull
		if (!syncedStr.isNullOrEmpty()) {
			return parseLrc(syncedStr)
		}

		val plainStr = jsonObject["plainLyrics"]?.jsonPrimitive?.contentOrNull
		if (!plainStr.isNullOrEmpty()) {
			return ParsedLyrics(
				plainStr.lineSequence()
					.map { LyricsLine(text = it.trim()) }
					.toList()
			)
		}

		if (jsonObject.containsKey("lyrics")) {
			val youlyResponse = jsonParser.decodeFromString<YoulyResponse>(jsonString)
			return parseYoulyResponse(youlyResponse)?.let { ParsedLyrics(it) }
		}

		return null
	}

	private fun parseYoulyResponse(response: YoulyResponse): List<LyricsLine>? {
		if (response.lyrics.isEmpty()) return null
		return response.lyrics.map { line ->
			LyricsLine(
				time = line.time.milliseconds,
				text = line.text,
				words = line.syllabus?.map { syl ->
					LyricsWord(syl.time.milliseconds, syl.duration.milliseconds, syl.text)
				}
			)
		}.sortedBy { it.time }
	}

	private fun parseLrc(input: String): ParsedLyrics {
		val lines = input.lineSequence().toList()

		if (!input.contains("[") && !input.contains("<")) {
			return ParsedLyrics(lines.map { LyricsLine(text = it.trim()) })
		}

		var translationLanguage: String? = null
		val rawLines = mutableListOf<RawLine>()

		for (raw in lines) {
			val line = raw.trim()
			if (line.isEmpty()) continue

			val trLang = TR_LANG.matchEntire(line)
			if (trLang != null) {
				translationLanguage = trLang.groupValues[1].ifBlank { null }
				continue
			}

			var time: Duration? = null
			var rest = line

			LINE_TIMESTAMP.find(line)?.let { match ->
				time = match.toDuration()
				rest = line.substring(match.range.last + 1)
			}

			var isTranslation = false
			val trimmedRest = rest.trimStart()
			if (trimmedRest.startsWith(TR_PREFIX, ignoreCase = true)) {
				isTranslation = true
				rest = trimmedRest.substring(TR_PREFIX.length)
			} else if (time == null && rest.startsWith("[")) {
				// leading non-timestamp tag like [ti:...] / [ar:...] / [offset:...]
				val close = rest.indexOf(']')
				if (close > 0) rest = rest.substring(close + 1)
			}

			val (text, words, dangling) = extractWords(rest, time)
			if (text.isEmpty() && time == null) continue
			if (isTranslation && text.isEmpty()) continue

			rawLines += RawLine(
				time = time,
				text = text,
				words = if (isTranslation) null else words,
				isTranslation = isTranslation,
				danglingWordIndex = if (isTranslation) -1 else dangling
			)
		}

		val sorted = rawLines.sortedBy { it.time }
		val fixed = fixDanglingWordDurations(sorted)
		return ParsedLyrics(
			lines = pairTranslations(fixed),
			translationLanguage = translationLanguage
		)
	}

	// splits a line's text on inline word tags, returning the plain text (which is always
	// the exact concatenation of the returned words so indexOf-based progress keeps working)
	// alongside the word list. words that have no trailing tag get a zero duration placeholder
	// (dangling) which is fixed once the surrounding lines are known.
	private fun extractWords(
		text: String,
		lineTime: Duration?
	): Triple<String, List<LyricsWord>?, Int> {
		val tags = (WORD_TAG_SQUARE.findAll(text) + WORD_TAG_ANGLE.findAll(text))
			.sortedBy { it.range.first }
			.toList()
		if (tags.isEmpty()) return Triple(text.trim(), null, -1)

		data class Piece(val time: Duration, val duration: Duration?, val text: String)

		val pieces = mutableListOf<Piece>()
		val prefix = text.substring(0, tags.first().range.first)
		if (prefix.isNotBlank()) {
			val start = lineTime ?: tags.first().toDuration()
			pieces += Piece(
				start,
				(tags.first().toDuration() - start).coerceAtLeast(Duration.ZERO),
				prefix.trimStart()
			)
		}
		for (i in tags.indices) {
			val segStart = tags[i].range.last + 1
			val segEnd = tags.getOrNull(i + 1)?.range?.first ?: text.length
			val seg = text.substring(segStart, segEnd)
			if (seg.isEmpty()) continue // pure timing marker, e.g. the ELRC end-of-line tag
			val start = tags[i].toDuration()
			val end = tags.getOrNull(i + 1)?.toDuration()
			pieces += Piece(start, end?.let { (it - start).coerceAtLeast(Duration.ZERO) }, seg)
		}

		val words = mutableListOf<LyricsWord>()
		var dangling = -1
		for (piece in pieces) {
			if (piece.text.isBlank()) {
				// whitespace-only segment between tags: fold it into the previous word
				// so the reconstructed text keeps its spacing
				val lastIndex = words.lastIndex
				if (lastIndex >= 0) {
					val last = words[lastIndex]
					words[lastIndex] = last.copy(
						text = last.text + piece.text,
						duration = piece.duration?.let { last.duration + it } ?: last.duration
					)
					if (piece.duration == null) dangling = lastIndex
				}
				continue
			}
			if (piece.duration == null) dangling = words.size
			words += LyricsWord(piece.time, piece.duration ?: Duration.ZERO, piece.text)
		}

		if (words.isEmpty()) return Triple(text.trim(), null, -1)

		// drop the trailing edge of the final word so the line text stays trimmed
		val lastIndex = words.lastIndex
		words[lastIndex] = words[lastIndex].let { it.copy(text = it.text.trimEnd()) }

		return Triple(words.joinToString("") { it.text }, words, dangling)
	}

	private fun fixDanglingWordDurations(sorted: List<RawLine>): List<RawLine> {
		return sorted.mapIndexed { i, raw ->
			val dangling = raw.danglingWordIndex
			val words = raw.words
			if (dangling < 0 || words == null || dangling >= words.size) return@mapIndexed raw
			val word = words[dangling]
			val nextTime = sorted.asSequence()
				.drop(i + 1)
				.mapNotNull { it.time }
				.firstOrNull { it > (raw.time ?: Duration.ZERO) }
			val lineEnd = nextTime ?: ((raw.time ?: word.time) + FALLBACK_LINE_DURATION)
			val duration = (lineEnd - word.time).coerceAtLeast(MIN_WORD_DURATION)
			raw.copy(
				words = words.mapIndexed { wi, w ->
					if (wi == dangling) w.copy(duration = duration) else w
				}
			)
		}
	}

	// translation lines share their timestamp with the original line; grouping by timestamp
	// keeps pairing independent of line order (Navidrome sorting is not guaranteed stable)
	private fun pairTranslations(sorted: List<RawLine>): List<LyricsLine> {
		val translationByTime = HashMap<Duration, String>()
		val mainTimes = HashSet<Duration>()
		for (line in sorted) {
			val time = line.time ?: continue
			if (line.isTranslation) {
				if (line.text.isNotEmpty()) translationByTime.putIfAbsent(time, line.text)
			} else {
				mainTimes += time
			}
		}

		val result = mutableListOf<LyricsLine>()
		for (line in sorted) {
			if (line.isTranslation) {
				val time = line.time
				if (time == null && line.text.isNotEmpty()) {
					val previous = result.lastOrNull()
					if (previous != null && previous.time == null && previous.translation == null) {
						result[result.lastIndex] = previous.copy(translation = line.text)
						continue
					}
				}
				// orphan translation without a partner line: keep it as a plain line
				val isOrphan = time == null || time !in mainTimes
				if (isOrphan && line.text.isNotEmpty()) {
					result += LyricsLine(time = line.time, text = line.text, words = line.words)
				}
				continue
			}
			result += LyricsLine(
				time = line.time,
				text = line.text,
				words = line.words,
				translation = line.time?.let(translationByTime::get)
			)
		}
		return result
	}

	// accepts mm:ss, mm:ss.x, mm:ss.xx and mm:ss.xxx, interpreting the fraction
	// as tenths, hundredths or milliseconds based on its digit count
	private fun MatchResult.toDuration(): Duration {
		val minutesPart = groupValues[1].toLong()
		val secondsPart = groupValues[2].toLong()
		val fraction = groupValues.getOrNull(3).orEmpty()
		val fractionMs = when (fraction.length) {
			1 -> fraction.toLong() * 100
			2 -> fraction.toLong() * 10
			3 -> fraction.toLong()
			else -> 0L
		}
		return minutesPart.minutes + secondsPart.seconds + fractionMs.milliseconds
	}
}

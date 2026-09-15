package com.vui.vaporwave.data.lyrics

import com.vui.vaporwave.model.LyricLine

/**
 * Parses standard .lrc synced-lyrics text: lines of `[mm:ss.xx]lyric text`, optionally with
 * multiple leading time tags sharing one line of text (e.g. a repeated chorus), and non-time
 * metadata tags (`[ti:...]`, `[ar:...]`, etc.) which this simply ignores since they never match
 * the digit-only time-tag pattern.
 */
object LrcParser {
    private val timeTagRegex = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(text: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        text.lineSequence().forEach { rawLine ->
            val matches = timeTagRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach

            val lyricText = rawLine.substring(matches.last().range.last + 1).trim()
            if (lyricText.isEmpty()) return@forEach

            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fraction = match.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                val timestampMs = minutes * 60_000L + seconds * 1000L + fractionMs
                lines.add(LyricLine(timestampMs = timestampMs, text = lyricText))
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}

package com.vui.vaporwave.model

/**
 * What [com.vui.vaporwave.data.MusicRepository.fetchLyrics] found for a track, if anything.
 * [Synced] (from a .lrc file) gets auto-scroll and tap-to-seek in the UI; [Plain] (embedded,
 * unsynced lyrics read straight from the file's own tags) is just a static scrollable block --
 * there's nothing to seek to when a line has no timestamp.
 */
sealed interface LyricsResult {
    data class Synced(val lines: List<LyricLine>) : LyricsResult
    data class Plain(val text: String) : LyricsResult
}

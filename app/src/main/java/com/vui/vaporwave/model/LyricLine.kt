package com.vui.vaporwave.model

/** One line of synced lyrics, timestamped to the position (ms) it should become active at. */
data class LyricLine(
    val timestampMs: Long,
    val text: String
)

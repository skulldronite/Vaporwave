package com.vui.vaporwave.data.lyrics

/**
 * Reads unsynced lyrics embedded in a track's own tags, dispatching by format the same way
 * [com.vui.vaporwave.data.tagging.AudioTagWriter] dispatches its writers. WAV and other formats
 * that essentially never carry lyrics in practice are left unhandled (return null) rather than
 * given a reader nothing would ever populate.
 */
object EmbeddedLyricsReader {
    fun read(bytes: ByteArray, formatBadge: String): String? = when (formatBadge) {
        "MP3" -> Mp3LyricsReader.read(bytes)
        "FLAC" -> FlacLyricsReader.read(bytes)
        "M4A" -> Mp4LyricsReader.read(bytes)
        else -> null
    }
}

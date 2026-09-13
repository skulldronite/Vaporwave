package com.vui.vaporwave.data.tagging

/**
 * Picks the right per-format byte-level tag writer. Each writer takes the whole original file as
 * a byte array and returns the whole new file as a byte array -- see the individual writers for
 * why that's safe (every format here delimits its metadata block(s) clearly enough that the
 * remaining audio data can always be copied through untouched).
 */
object AudioTagWriter {

    /** Formats with no reliable, widely-supported way to embed writable tags at all. */
    val unsupportedFormats = setOf("AAC", "OGG", "OPUS", "ALAC", "AIFF", "WEBM", "AUDIO")

    fun rewrite(original: ByteArray, formatBadge: String, fields: MetadataFields): ByteArray {
        return when (formatBadge) {
            "MP3" -> Mp3TagWriter.rewrite(original, fields)
            "FLAC" -> FlacTagWriter.rewrite(original, fields)
            "WAV" -> WavTagWriter.rewrite(original, fields)
            "M4A" -> Mp4TagWriter.rewrite(original, fields)
            else -> throw UnsupportedOperationException("Editing $formatBadge tags isn't supported yet")
        }
    }
}

sealed class TagSaveResult {
    object Success : TagSaveResult()
    data class NeedsPermission(val intentSender: android.content.IntentSender) : TagSaveResult()
    data class Failure(val message: String) : TagSaveResult()
}

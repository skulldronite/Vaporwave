package com.vui.vaporwave.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * Represents a playable audio track with metadata, artwork, and format specifications.
 * Supports any audio format compatible with AndroidX Media3 (MP3, FLAC, WAV, AAC, OGG, OPUS, M4A, etc.)
 *
 * Marked [Immutable] because every field is a val and never mutated after construction. Without it
 * the Compose compiler infers this class as unstable (android.net.Uri carries no stability info),
 * which costs skippability on every composable that takes a track.
 */
@Immutable
data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: Uri? = null,
    val artworkUri: Uri? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val sampleRateHz: Int = 44100,
    val bitDepth: Int = 16,
    val bitrateKbps: Int = 0,
    val isDemoTrack: Boolean = false,
    val dateAddedMs: Long = 0L,
    /** 1-based position within its album, from file metadata. 0 means the tag was absent. */
    val trackNumber: Int = 0,
    /** Disc number for multi-disc albums, from file metadata. 0 for single-disc/untagged. */
    val discNumber: Int = 0,
    /** Release year from file metadata, straight from MediaStore's own YEAR column. 0 if untagged. */
    val year: Int = 0
) {
    /**
     * Human readable format identifier (e.g. FLAC, MP3, WAV, OPUS, AAC, OGG)
     *
     * Computed once and cached rather than on every read: the search filter reads this for every
     * track on every keystroke, and it allocates two lowercased strings per call.
     */
    val formatBadge: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val mime = mimeType?.lowercase(Locale.ROOT) ?: ""
        val uriStr = contentUri?.toString()?.lowercase(Locale.ROOT) ?: ""
        when {
            mime.contains("flac") || uriStr.endsWith(".flac") -> "FLAC"
            mime.contains("wav") || uriStr.endsWith(".wav") -> "WAV"
            mime.contains("opus") || uriStr.endsWith(".opus") -> "OPUS"
            mime.contains("ogg") || uriStr.endsWith(".ogg") -> "OGG"
            mime.contains("mp3") || mime.contains("mpeg") || uriStr.endsWith(".mp3") -> "MP3"
            mime.contains("aac") || uriStr.endsWith(".aac") -> "AAC"
            mime.contains("mp4") || mime.contains("m4a") || uriStr.endsWith(".m4a") -> "M4A"
            mime.contains("alac") || uriStr.endsWith(".alac") -> "ALAC"
            mime.contains("aiff") || uriStr.endsWith(".aiff") -> "AIFF"
            mime.contains("webm") || uriStr.endsWith(".webm") -> "WEBM"
            else -> "AUDIO"
        }
    }

    /**
     * Technical audio quality string (e.g. "FLAC · 24-bit · 96 kHz" or "MP3 · 320 kbps")
     */
    val technicalDetails: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        run {
            val badge = formatBadge
            val parts = mutableListOf(badge)
            if (bitDepth > 16) {
                parts.add("${bitDepth}-bit")
            }
            if (sampleRateHz > 0) {
                val khz = sampleRateHz / 1000f
                parts.add(if (khz == khz.toInt().toFloat()) "${khz.toInt()} kHz" else "%.1f kHz".format(khz))
            }
            if (bitrateKbps > 0) {
                parts.add("${bitrateKbps} kbps")
            } else if (badge == "FLAC" || badge == "WAV") {
                parts.add("Lossless")
            }
            parts.joinToString(" · ")
        }
    }

    /**
     * Formats duration in mm:ss or hh:mm:ss
     */
    val formattedDuration: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}

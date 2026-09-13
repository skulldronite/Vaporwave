package com.vui.vaporwave.model

/**
 * Metadata fields worth showing on Track Details but not worth reading for every track up front
 * during the full-library scan (genre in particular requires opening each file individually via
 * MediaMetadataRetriever, which would slow that scan down considerably on a large library).
 * Fetched on demand only when Track Details is actually opened for a given track.
 */
data class ExtendedTrackMetadata(
    val genre: String? = null,
    val recordingDate: String? = null,
    val filePath: String? = null,
    val albumArtist: String? = null,
    /** 0 when unknown -- the eager library scan never populates AudioTrack.bitrateKbps either. */
    val bitrateKbps: Int = 0
)

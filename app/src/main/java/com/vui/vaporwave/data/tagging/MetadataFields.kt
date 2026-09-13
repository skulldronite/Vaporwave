package com.vui.vaporwave.data.tagging

/**
 * Editable tag fields from the Metadata Editor. Every text field is the literal value to write --
 * blank means "clear this tag", never "leave it alone" -- so callers must prefill fields with the
 * track's current values before handing this to a writer, or an untouched field will wipe the tag.
 */
data class MetadataFields(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val recordingDate: String,
    val trackNumber: Int,
    val discNumber: Int,
    /** New cover art to embed, or null to leave existing artwork untouched. */
    val artworkJpeg: ByteArray? = null
)

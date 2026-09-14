package com.vui.vaporwave.model

import androidx.compose.runtime.Immutable

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val trackIds: List<Long> = emptyList(),
    /** Id of the track whose artwork stands in for this playlist's own; null means "not chosen
     *  yet", falling back to the first track's artwork. */
    val artworkTrackId: Long? = null
)

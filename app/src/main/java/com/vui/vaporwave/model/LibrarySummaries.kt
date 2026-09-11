package com.vui.vaporwave.model

import android.net.Uri
import androidx.compose.runtime.Immutable

// Both carry an android.net.Uri, which the Compose compiler can't infer stability for -- without
// the annotation these are treated as unstable and cost skippability in the album/artist lists.
@Immutable
data class AlbumSummary(
    val name: String,
    val artist: String,
    val artworkUri: Uri?,
    val trackCount: Int,
    val latestDateAddedMs: Long = 0L
)

@Immutable
data class ArtistSummary(
    val name: String,
    val trackCount: Int,
    val artworkUri: Uri?,
    val latestDateAddedMs: Long = 0L
)

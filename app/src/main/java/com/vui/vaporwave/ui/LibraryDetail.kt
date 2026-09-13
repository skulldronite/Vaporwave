package com.vui.vaporwave.ui

import android.net.Uri
import com.vui.vaporwave.model.AudioTrack

/** The three at-a-glance categories shown above the playlist list. */
enum class SpotlightCategory(val label: String) {
    RECENTLY_ADDED("Recently Added"),
    MOST_PLAYED("Most Played"),
    RECENTLY_PLAYED("Recently Played")
}

/**
 * Which drill-down detail (if any) is currently open over the library, regardless of which tab
 * it was opened from. Held at the app level (not inside LibraryScreen) so the detail can render
 * as a full-screen overlay -- covering the top bar and status bar too -- the same way the search
 * card does, rather than being confined to the library tab's own content area.
 */
sealed class LibraryDetail {
    data class Album(val name: String) : LibraryDetail()
    data class Artist(val name: String) : LibraryDetail()
    data class PlaylistDetail(val playlistId: Long) : LibraryDetail()
    data class Spotlight(val category: SpotlightCategory) : LibraryDetail()
}

/**
 * What the metadata editor is currently open for: a single track's own fields, or an album's
 * shared fields (title, album artist, genre, recording date, cover art) applied across every
 * track in it at once.
 */
sealed class MetadataEditTarget {
    data class Track(val track: AudioTrack) : MetadataEditTarget()
    data class Album(
        val name: String,
        val artist: String,
        val artworkUri: Uri?,
        val tracks: List<AudioTrack>
    ) : MetadataEditTarget()
}

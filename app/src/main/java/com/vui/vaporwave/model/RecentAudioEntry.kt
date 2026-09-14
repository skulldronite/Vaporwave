package com.vui.vaporwave.model

import android.net.Uri
import androidx.compose.runtime.Immutable

/**
 * One file opened via the Files page's SAF picker (or an incoming "Open with" intent). Lighter
 * than [AudioTrack] on purpose: these aren't part of the scanned MediaStore library, so most of
 * that model's fields (disc/track number, date added, artwork, ...) don't apply -- the only
 * things worth remembering here are which file it was and when it was last opened. Re-opening one
 * resolves a full [AudioTrack] from [uri] again via MusicRepository.resolveTrackFromUri, the same
 * way opening it the first time did.
 */
@Immutable
data class RecentAudioEntry(
    val uri: Uri,
    val title: String,
    val openedAtMs: Long,
    val artworkUri: Uri? = null,
    val sizeBytes: Long = 0L
)

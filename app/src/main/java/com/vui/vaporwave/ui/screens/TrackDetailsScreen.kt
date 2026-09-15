package com.vui.vaporwave.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.vui.vaporwave.model.AudioTrack
import com.vui.vaporwave.model.ExtendedTrackMetadata
import java.util.Locale

/**
 * Read-only details for a single track: hero art (same treatment as album/artist detail), then
 * every field worth surfacing beyond what's already shown while browsing -- genre, recording
 * date, and file path in particular are fetched on demand by the caller (see
 * MusicRepository.fetchExtendedMetadata) rather than being part of AudioTrack itself.
 */
@Composable
fun TrackDetailsScreen(
    track: AudioTrack,
    extended: ExtendedTrackMetadata?,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Track Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onEditClick) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit track details")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        if (track.artworkUri != null) {
                            val context = LocalContext.current
                            AsyncImage(
                                model = remember(track.artworkUri) {
                                    ImageRequest.Builder(context)
                                        .data(track.artworkUri)
                                        .size(Size(400, 400))
                                        .build()
                                },
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            val trackNumberText = if (track.trackNumber > 0) track.trackNumber.toString() else "Unknown"
            val rows = listOf(
                "Album" to track.album,
                "Genre" to (extended?.genre?.takeIf { it.isNotBlank() } ?: "Unknown"),
                "Track Length" to track.formattedDuration,
                "Recording Date" to (extended?.recordingDate?.takeIf { it.isNotBlank() } ?: "Unknown"),
                "Track Number" to trackNumberText,
                "Format" to formatOnly(track),
                "Bitrate" to formatBitrateRow(track, extended),
                "Size of File" to formatFileSize(track.sizeBytes),
                "Path of Storage" to (extended?.filePath ?: track.contentUri?.toString() ?: "Unknown")
            )
            items(rows) { (label, value) ->
                DetailRow(label = label, value = value)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Format badge + bit depth only -- sample rate and bitrate live in their own "Bitrate" row now,
 * so unlike track.technicalDetails (which folds all of that together), this deliberately leaves
 * both out.
 */
private fun formatOnly(track: AudioTrack): String {
    val parts = mutableListOf(track.formatBadge)
    if (track.bitDepth > 16) {
        parts.add("${track.bitDepth}-bit")
    }
    return parts.joinToString(" · ")
}

/**
 * Standalone bitrate-then-sample-rate row, separate from formatWithBitrate above (which folds
 * bitrate into the combined Format string) -- same fallback chain (scanned value first, then the
 * on-demand fetch behind Track Details) for bitrate, plus the sample rate on its own.
 */
private fun formatBitrateRow(track: AudioTrack, extended: ExtendedTrackMetadata?): String {
    val bitrateKbps = track.bitrateKbps.takeIf { it > 0 } ?: extended?.bitrateKbps?.takeIf { it > 0 }
    val sampleRateKhz = track.sampleRateHz.takeIf { it > 0 }?.let { it / 1000f }
    return listOfNotNull(
        bitrateKbps?.let { "$it kbps" },
        sampleRateKhz?.let { khz -> if (khz == khz.toInt().toFloat()) "${khz.toInt()} kHz" else "%.1f kHz".format(khz) }
    ).joinToString(" · ").ifBlank { "Unknown" }
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "Unknown"
    val kb = sizeBytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", kb)
    }
}

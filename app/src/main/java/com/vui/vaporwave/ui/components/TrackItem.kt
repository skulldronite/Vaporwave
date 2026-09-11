package com.vui.vaporwave.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.vui.vaporwave.model.AudioTrack
import com.vui.vaporwave.theme.VaporCyan
import com.vui.vaporwave.theme.VaporPink

@Composable
fun TrackItem(
    track: AudioTrack,
    isPlayingThisTrack: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
    /** Shown in artwork's place (from the file's own metadata) when [showArtwork] is false. */
    trackNumber: Int? = null,
    /** Briefly true to flash this row -- e.g. an alphabet-scrollbar jump on a list too short to
     *  actually scroll to it. Caller is responsible for clearing it back to false after a beat. */
    isHighlighted: Boolean = false
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isHighlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            isPlayingThisTrack -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "track_bg"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Artwork thumbnail or decorative aesthetic fallback -- skipped entirely inside
            // an album detail, where every row would just repeat the same hero image already
            // shown above the list.
            if (showArtwork) {
                val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                val primaryContainer = MaterialTheme.colorScheme.primaryContainer
                val artworkBrush = remember(isPlayingThisTrack, surfaceVariant, primaryContainer) {
                    Brush.linearGradient(
                        listOf(
                            if (isPlayingThisTrack) VaporPink else surfaceVariant,
                            if (isPlayingThisTrack) VaporCyan else primaryContainer
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(artworkBrush),
                    contentAlignment = Alignment.Center
                ) {
                    // The icon sits underneath and the artwork paints over it once loaded, rather
                    // than using SubcomposeAsyncImage's loading/error slots -- subcomposition runs
                    // a nested composition pass per row during layout, which is measurable while
                    // scrolling.
                    Icon(
                        imageVector = if (isPlayingThisTrack) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (isPlayingThisTrack) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )

                    if (track.artworkUri != null) {
                        val context = LocalContext.current
                        AsyncImage(
                            // Embedded album art can be 1400-3000px square; request a thumbnail
                            // sized for this 52dp row instead of decoding at full resolution.
                            model = remember(track.artworkUri) {
                                ImageRequest.Builder(context)
                                    .data(track.artworkUri)
                                    .size(Size(128, 128))
                                    .build()
                            },
                            contentDescription = "Album art",
                            modifier = Modifier.size(52.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))
            } else if (trackNumber != null && trackNumber > 0) {
                // No artwork here (already shown once in the hero above the list) -- the freed
                // slot instead carries this track's position from its own file metadata.
                Box(
                    modifier = Modifier.size(width = 28.dp, height = 52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = trackNumber.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isPlayingThisTrack) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPlayingThisTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            // Metadata: Title, Artist, and Album
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPlayingThisTrack) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPlayingThisTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

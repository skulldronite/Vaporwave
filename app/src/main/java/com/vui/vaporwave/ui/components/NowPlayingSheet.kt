package com.vui.vaporwave.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.vui.vaporwave.model.AudioTrack
import com.vui.vaporwave.theme.VaporCyan
import com.vui.vaporwave.theme.VaporMint
import com.vui.vaporwave.theme.VaporPink
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    track: AudioTrack?,
    isPlaying: Boolean,
    /** Read lazily so the position tick doesn't recompose the whole sheet -- see [SeekSection]. */
    positionProvider: () -> Long,
    durationMs: Long,
    playbackSpeed: Float,
    isSlowedAndReverb: Boolean,
    repeatMode: Int,
    isShuffle: Boolean,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleSlowedAndReverb: () -> Unit,
    onOpenEffects: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Applied to the sheet's header row so the caller can make it draggable. The sheet is no
     * longer a ModalBottomSheet -- its position is driven by the caller so that a drag starting
     * on the mini player can carry straight through into moving this sheet.
     */
    headerDragModifier: Modifier = Modifier
) {
    if (track == null) return

    val volumeState = rememberVolumeState()
    var isVolumeSliderVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Dismiss Button & Title
            Row(
                modifier = headerDragModifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                Row {
                    IconButton(onClick = { isVolumeSliderVisible = !isVolumeSliderVisible }) {
                        Icon(
                            imageVector = when {
                                volumeState.volume == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                                volumeState.volume < volumeState.maxVolume / 2 -> Icons.AutoMirrored.Filled.VolumeDown
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = "Volume",
                            tint = if (isVolumeSliderVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onOpenEffects) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Equalizer",
                            tint = if (isSlowedAndReverb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Volume slider -- expands to the sheet's full width (rather than a small popup) so
            // its step spacing is as spread out as the device's real STREAM_MUSIC range allows,
            // and animates open/closed instead of just appearing/vanishing.
            AnimatedVisibility(
                visible = isVolumeSliderVisible,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = volumeState.volume.toFloat(),
                        onValueChange = { volumeState.applyVolume(it.roundToInt()) },
                        valueRange = 0f..volumeState.maxVolume.toFloat(),
                        steps = (volumeState.maxVolume - 1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        // Shown as its 0-100 equivalent rather than the device's raw step index
                        // (e.g. step 7 of a 15-step range reads as "47", not "7") -- the real
                        // step count still drives the slider's actual granularity above.
                        text = volumeState.volumePercent.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large Album Artwork with Vaporwave Ambient Glow
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .shadow(
                        // Colored ambient/spot shadows need a multi-pass fragment shader; keeping
                        // elevation modest avoids GPU pipeline stalls during sheet drag gestures.
                        elevation = 10.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = VaporPink,
                        spotColor = VaporCyan
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                VaporPink.copy(alpha = 0.85f),
                                VaporCyan.copy(alpha = 0.85f),
                                VaporMint.copy(alpha = 0.85f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                val artworkPlaceholder: @Composable () -> Unit = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(96.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "V A P O R W A V E",
                            style = MaterialTheme.typography.titleMedium.copy(
                                letterSpacing = 4.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                artworkPlaceholder()
                if (track.artworkUri != null) {
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = "Album Artwork",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Track Title & Artist
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Favourite (center) & Add to Playlist (right) Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(48.dp))

                IconButton(onClick = onToggleFavourite) {
                    Icon(
                        imageVector = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavourite) "Remove from Favourites" else "Add to Favourites",
                        tint = if (isFavourite) VaporPink else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onAddToPlaylist) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Add to Playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Audio Format & Quality Specification Pill Badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = track.technicalDetails,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Progress Slider & Timestamps. Split into its own composable so the position tick
            // recomposes only this row rather than the entire sheet (artwork, marquees, chips
            // and transport buttons all sit in this same function).
            SeekSection(
                positionProvider = positionProvider,
                durationMs = durationMs,
                onSeekTo = onSeekTo
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Skip Previous
                IconButton(onClick = onSkipPrevious) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Rewind 10s
                IconButton(onClick = { onSeekBy(-10000L) }) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind 10s",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Play / Pause FAB
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(68.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Fast Forward 30s
                IconButton(onClick = { onSeekBy(30000L) }) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 30s",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Skip Next
                IconButton(onClick = onSkipNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Mode
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Vaporwave Slowed + Reverb Quick Action Pill
            FilterChip(
                selected = isSlowedAndReverb,
                onClick = onToggleSlowedAndReverb,
                label = {
                    Text(
                        text = if (isSlowedAndReverb) "A E S T H E T I C  SLOWED (0.85x)" else "Slowed + Reverb Effect",
                        fontWeight = if (isSlowedAndReverb) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * The seek bar and its two timestamps. Isolated from [NowPlayingSheet] so that the playback
 * position -- which updates four times a second -- invalidates only this small subtree.
 */
@Composable
private fun SeekSection(
    positionProvider: () -> Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit
) {
    var isUserDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val totalDuration = durationMs.coerceAtLeast(1L).toFloat()
    // The snapshot read happens here, inside the small composable, not in the sheet's body.
    val position = positionProvider()
    val sliderValue = if (isUserDragging) dragPosition else position.toFloat()

    Slider(
        value = sliderValue.coerceIn(0f, totalDuration),
        onValueChange = {
            isUserDragging = true
            dragPosition = it
        },
        onValueChangeFinished = {
            isUserDragging = false
            onSeekTo(dragPosition.toLong())
        },
        valueRange = 0f..totalDuration,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val displayPosition = if (isUserDragging) dragPosition.toLong() else position
        Text(
            text = formatTime(displayPosition),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatTime(durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Live handle on the real AudioManager media stream -- half the slider bound to [volume] means
 * half the actual system media volume, the same way the hardware rocker or the system's own
 * volume dialog behaves, rather than an app-side gain layered on top of it. [maxVolume] is
 * whatever the device's own STREAM_MUSIC step count is: fixed by the OS/OEM and not something an
 * app can exceed, so this reads it live instead of assuming a fixed range -- some devices expose
 * noticeably more steps than others.
 */
private class VolumeState(private val audioManager: AudioManager) {
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var volume by mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        private set

    // The device's real step index, rescaled to a universal 0-100 so it reads the same
    // regardless of how many steps this particular device's STREAM_MUSIC actually has (e.g. a
    // device with 15 steps and one with 30 both show "50" at the halfway point).
    val volumePercent: Int
        get() = (volume * 100f / maxVolume).roundToInt()

    fun applyVolume(value: Int) {
        volume = value
        // flags=0: suppress the system's own volume UI/toast, since this slider replaces it.
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
    }

    fun refreshFromSystem() {
        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }
}

/**
 * Keeps [VolumeState] in sync if the volume changes from outside this sheet -- the hardware
 * rocker, another app, or a Bluetooth device's own volume control.
 */
@Composable
private fun rememberVolumeState(): VolumeState {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val state = remember { VolumeState(audioManager) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                state.refreshFromSystem()
            }
        }
        // AudioManager.VOLUME_CHANGED_ACTION is a hidden/non-public constant -- its literal
        // value is stable API surface (system apps like Samsung Music rely on the same
        // broadcast), just not exposed as a symbol in the public SDK.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    return state
}

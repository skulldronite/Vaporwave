package com.vui.vaporwave.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import com.vui.vaporwave.model.LyricLine
import com.vui.vaporwave.model.LyricsResult
import com.vui.vaporwave.theme.VaporCyan
import com.vui.vaporwave.theme.VaporMint
import com.vui.vaporwave.theme.VaporPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** The lyrics tile's dark-mode background -- deliberately not any theme token, since the tile
 *  swaps to a plain white/dark-gray pair regardless of which color scheme (Neon, Material You,
 *  OLED, etc.) is actually active, rather than tracking any of them. */
private val LyricsDarkBackground = Color(0xFF2A2A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    track: AudioTrack?,
    isPlaying: Boolean,
    /** Read lazily so the position tick doesn't recompose the whole sheet -- see [SeekSection]. */
    positionProvider: () -> Long,
    durationMs: Long,
    /** Null means no lyrics (synced or embedded) for [track] -- tapping the album art does nothing. */
    lyrics: LyricsResult?,
    playbackSpeed: Float,
    isSlowedAndReverb: Boolean,
    repeatMode: Int,
    isShuffle: Boolean,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    /** Whether the shared reachability pull (see [ReachabilityPullBox]) is currently forced fully open. */
    isOneHandedModeEnabled: Boolean,
    onToggleOneHandedMode: () -> Unit,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSetSpeedAndPitch: (Float, Float) -> Unit,
    onOpenEffects: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenSettings: () -> Unit,
    onShare: () -> Unit,
    onDeleteTrack: () -> Unit,
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
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    // Back to album art on every track change, rather than carrying "showing lyrics" over onto
    // whatever plays next.
    LaunchedEffect(track.id) { showLyrics = false }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this track?") },
            text = { Text("\"${track.title}\" will be permanently deleted from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDeleteTrack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
            // Header: Dismiss Button & Icon Actions
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

                // Shifted right of where the row's own edge naturally lands (bleeding a bit into
                // the sheet's 24dp side margin), closer to the true edge of the screen.
                Row(modifier = Modifier.offset(x = 12.dp)) {
                    IconButton(onClick = { isVolumeSliderVisible = !isVolumeSliderVisible }) {
                        VolumeLevelIcon(
                            level = when {
                                volumeState.volumePercent <= 30 -> 1
                                volumeState.volumePercent <= 70 -> 2
                                else -> 3
                            },
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

                    Box {
                        IconButton(onClick = { isOverflowMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = isOverflowMenuExpanded,
                            onDismissRequest = { isOverflowMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Album") },
                                leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) },
                                onClick = {
                                    isOverflowMenuExpanded = false
                                    onOpenAlbum()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Artist") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                onClick = {
                                    isOverflowMenuExpanded = false
                                    onOpenArtist()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    isOverflowMenuExpanded = false
                                    showDeleteConfirmation = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    isOverflowMenuExpanded = false
                                    onShare()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    isOverflowMenuExpanded = false
                                    onOpenSettings()
                                }
                            )
                        }
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

            // Large Album Artwork with Vaporwave Ambient Glow -- a halo that pulses while playing
            // (a slow breathing loop layered on top of an eased on/off envelope, so starting or
            // stopping playback isn't an abrupt cut) and settles to a small static glow otherwise.
            val glowTransition = rememberInfiniteTransition(label = "art_glow")
            val glowPulse by glowTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "art_glow_pulse"
            )
            val playingEnvelope by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0f,
                animationSpec = tween(500),
                label = "art_glow_envelope"
            )
            val glowElevation = 10.dp + 18.dp * glowPulse * playingEnvelope

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .shadow(
                        // Colored ambient/spot shadows need a multi-pass fragment shader; keeping
                        // elevation modest avoids GPU pipeline stalls during sheet drag gestures.
                        elevation = glowElevation,
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
                    )
                    // Tapping toggles synced lyrics over the artwork -- a no-op when this track
                    // has none, per the feature's own spec, rather than showing an empty view.
                    .clickable(enabled = lyrics != null) { showLyrics = !showLyrics },
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

                Crossfade(targetState = showLyrics && lyrics != null, label = "artwork_lyrics") { showingLyrics ->
                    val currentLyrics = lyrics
                    if (showingLyrics && currentLyrics != null) {
                        // Luminance-based rather than keyed off ThemeMode/useOledBlack/etc
                        // directly -- this reads correctly no matter which of Neon, Material You,
                        // standard Material 3, or OLED black actually resolved the current
                        // background, rather than needing to special-case each one here too.
                        val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        val lyricsBackground = if (isDarkSurface) LyricsDarkBackground else Color.White
                        val lyricsTextColor = if (isDarkSurface) Color.White else Color.Black

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(lyricsBackground)
                        ) {
                            when (currentLyrics) {
                                is LyricsResult.Synced -> LyricsView(
                                    lines = currentLyrics.lines,
                                    positionProvider = positionProvider,
                                    onSeekTo = onSeekTo,
                                    textColor = lyricsTextColor,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp)
                                )
                                is LyricsResult.Plain -> PlainLyricsView(
                                    text = currentLyrics.text,
                                    textColor = lyricsTextColor,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp)
                                )
                            }
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center) {
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
                    }
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

            Spacer(modifier = Modifier.height(20.dp))

            // Slowed & Favourite & Nightcore sit in a cluster centered on the heart -- Slowed and
            // Nightcore are the same size, so centering the cluster as a whole (rather than
            // spreading three unevenly-sized groups across the row with SpaceBetween, which used
            // to leave the heart off-center) puts the heart exactly in the middle. Add to Playlist
            // is unrelated to that cluster and stays pinned to the row's trailing edge.
            // Slowed/Nightcore are an exclusive pair, not independent toggles: tapping the active
            // one turns it back off (Standard, 1.0x); tapping the other one switches straight to
            // it. There's no separate "Standard" button any more -- 1.0x is just the state where
            // neither preset is selected.
            Box(modifier = Modifier.fillMaxWidth()) {
                val isSlowed = abs(playbackSpeed - 0.85f) < 0.01f
                val isNightcore = abs(playbackSpeed - 1.25f) < 0.01f

                // Mirrors Add to Playlist's placement on the opposite edge -- both are plain
                // IconButtons flush against the Box's own bounds, so they land the same distance
                // from their respective edges with no extra spacing math needed.
                IconButton(
                    onClick = onToggleOneHandedMode,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    OneHandedModeIcon(
                        tint = if (isOneHandedModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PresetIconButton(
                        icon = Icons.Default.Bedtime,
                        label = "Slowed",
                        selected = isSlowed,
                        onClick = {
                            if (isSlowed) onSetSpeedAndPitch(1f, 1f) else onSetSpeedAndPitch(0.85f, 0.85f)
                        }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // A small celebration when the track is newly favourited -- a bounce, a little
                    // wiggle, and a ring pulsing outward and fading -- all only on the way in. An
                    // unfavourite is a plain, unanimated icon swap, no effects.
                    val heartScale = remember { Animatable(1f) }
                    val heartRotation = remember { Animatable(0f) }
                    val heartRingProgress = remember { Animatable(0f) }
                    LaunchedEffect(isFavourite) {
                        if (isFavourite) {
                            heartScale.snapTo(0.5f)
                            heartRotation.snapTo(-20f)
                            heartRingProgress.snapTo(0f)
                            launch { heartScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                            launch { heartRotation.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
                            launch { heartRingProgress.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
                        } else {
                            heartScale.snapTo(1f)
                            heartRotation.snapTo(0f)
                            heartRingProgress.snapTo(0f)
                        }
                    }
                    Box(contentAlignment = Alignment.Center) {
                        if (heartRingProgress.value > 0f && heartRingProgress.value < 1f) {
                            Canvas(modifier = Modifier.size(48.dp)) {
                                val ringAlpha = 1f - heartRingProgress.value
                                val ringRadius = size.minDimension / 2f * (0.35f + heartRingProgress.value * 0.65f)
                                drawCircle(
                                    color = VaporPink.copy(alpha = ringAlpha * 0.6f),
                                    radius = ringRadius,
                                    style = Stroke(width = size.minDimension * 0.05f)
                                )
                            }
                        }
                        IconButton(onClick = onToggleFavourite) {
                            Icon(
                                imageVector = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavourite) "Remove from Favourites" else "Add to Favourites",
                                tint = if (isFavourite) VaporPink else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(28.dp)
                                    .graphicsLayer {
                                        scaleX = heartScale.value
                                        scaleY = heartScale.value
                                        rotationZ = heartRotation.value
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    PresetIconButton(
                        icon = Icons.Default.Bolt,
                        label = "Nightcore",
                        selected = isNightcore,
                        onClick = {
                            if (isNightcore) onSetSpeedAndPitch(1f, 1f) else onSetSpeedAndPitch(1.25f, 1.25f)
                        }
                    )
                }

                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Add to Playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
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

            // Main Playback Controls. The three buttons on each side of the play/pause FAB sit in
            // their own equal-width cell (Modifier.weight(1f)) rather than relying on
            // Arrangement.SpaceEvenly to land on a centered result by coincidence of the icons'
            // own sizes -- six equal-width cells flanking one fixed-size FAB is centered by
            // construction, regardless of how any individual icon inside a cell is sized.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button -- mirror-flips across its vertical axis and back on every tap,
                // which for this symmetric two-arrow glyph reads as the arrows crossing over to
                // the opposite side and returning.
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val shuffleTint by animateColorAsState(
                        targetValue = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "shuffle_tint"
                    )
                    val shuffleFlip = remember { Animatable(0f) }
                    val scope = rememberCoroutineScope()
                    BouncyIconButton(onClick = {
                        scope.launch {
                            shuffleFlip.animateTo(1f, tween(200))
                            shuffleFlip.animateTo(0f, tween(200))
                        }
                        onToggleShuffle()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = shuffleTint,
                            modifier = Modifier.graphicsLayer { scaleX = 1f - 2f * shuffleFlip.value }
                        )
                    }
                }

                // Skip Previous -- bumps a little bigger then settles back, on every tap.
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val previousBump = remember { Animatable(1f) }
                    var previousBumpTrigger by remember { mutableStateOf(0) }
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(previousBumpTrigger) {
                        if (previousBumpTrigger > 0) {
                            previousBump.animateTo(1.25f, tween(120))
                            previousBump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                    BouncyIconButton(onClick = {
                        previousBumpTrigger++
                        onSkipPrevious()
                    }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer { scaleX = previousBump.value; scaleY = previousBump.value }
                        )
                    }
                }

                // Rewind 5s
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SeekArrowButton(
                        forward = false,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onSeekBy(-5000L) }
                    )
                }

                // Play / Pause FAB -- fixed size, no weight, so it always sits exactly between
                // the two sets of three equal-width cells on either side of it. Bounces on press
                // like every other transport button, and morphs its own glyph between the two
                // bars and the triangle rather than crossfading between two separate icons.
                val fabInteractionSource = remember { MutableInteractionSource() }
                val isFabPressed by fabInteractionSource.collectIsPressedAsState()
                val fabScale by animateFloatAsState(
                    targetValue = if (isFabPressed) 0.88f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    label = "fab_press_scale"
                )
                FilledIconButton(
                    onClick = onPlayPause,
                    interactionSource = fabInteractionSource,
                    modifier = Modifier
                        .size(68.dp)
                        .graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                        },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    MorphingPlayPauseIcon(
                        isPlaying = isPlaying,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Forward 5s
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SeekArrowButton(
                        forward = true,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onSeekBy(5000L) }
                    )
                }

                // Skip Next -- same bump as Previous.
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val nextBump = remember { Animatable(1f) }
                    var nextBumpTrigger by remember { mutableStateOf(0) }
                    LaunchedEffect(nextBumpTrigger) {
                        if (nextBumpTrigger > 0) {
                            nextBump.animateTo(1.25f, tween(120))
                            nextBump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                    BouncyIconButton(onClick = {
                        nextBumpTrigger++
                        onSkipNext()
                    }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer { scaleX = nextBump.value; scaleY = nextBump.value }
                        )
                    }
                }

                // Repeat Mode -- OFF shows just "1", REPEAT_ONE morphs a ring of arrows in around
                // it, REPEAT_ALL fades the "1" back out leaving just the ring, and back to OFF
                // fades the ring back out -- see [RepeatModeIcon].
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val repeatTint by animateColorAsState(
                        targetValue = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "repeat_tint"
                    )
                    BouncyIconButton(onClick = onToggleRepeat) {
                        RepeatModeIcon(repeatMode = repeatMode, tint = repeatTint)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * A phone glyph with a small hand badge in the corner -- there's no single Material icon for
 * "one-handed mode", so this layers [Icons.Default.TouchApp] over [Icons.Outlined.Smartphone].
 * The outlined variant is a thin stroke tracing just the phone's silhouette (no solid fill), so
 * its bezel reads noticeably thinner than the filled version's solid chrome-thickness edge.
 */
@Composable
private fun OneHandedModeIcon(tint: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.Smartphone,
            contentDescription = "One-handed mode",
            tint = tint,
            modifier = Modifier.fillMaxSize()
        )
        Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(11.dp)
                .align(Alignment.BottomEnd)
        )
    }
}

/**
 * The Slowed/Nightcore toggle buttons flanking the favourite icon. Icon-only (no label) so it
 * sits comfortably in the same row as the other icon-sized actions there, unlike the larger
 * labeled picker this replaced.
 */
@Composable
private fun PresetIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * An [IconButton] that scales down slightly while pressed and springs back on release --
 * every transport control (shuffle, skip, seek-by-5, repeat) uses this instead of a plain
 * IconButton so the whole row has consistent tactile press feedback beyond just the ripple.
 */
@Composable
private fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "bouncy_press_scale"
    )
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        content = content
    )
}

/**
 * The Rewind/Forward 5s control, drawn as two independent layers so only the arrow spins --
 * Material's own Forward5/Replay5 glyphs fuse the arrow and the digit into one path, which would
 * spin the "5" right along with the arrow. [forward] mirrors the arrow horizontally for the
 * rewind direction. Each tap adds one more full turn on top of whatever rotation is already
 * there (never resetting to 0), so the arrow keeps spinning further in the same direction on
 * repeated taps rather than snapping back before turning again.
 */
@Composable
private fun SeekArrowButton(forward: Boolean, tint: Color, onClick: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    BouncyIconButton(onClick = {
        scope.launch {
            rotation.animateTo(rotation.value + if (forward) 360f else -360f, tween(500))
        }
        onClick()
    }) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        rotationZ = rotation.value
                        scaleX = if (forward) 1f else -1f
                    }
            ) {
                val strokeWidth = size.minDimension * 0.09f
                val radius = size.minDimension * 0.36f
                val center = Offset(size.width / 2f, size.height / 2f)
                // A ~270 degree arc, open at the bottom-right where the arrowhead sits.
                drawArc(
                    color = tint,
                    startAngle = -225f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                // Arrowhead at the arc's open end, pointing clockwise.
                val tipAngle = Math.toRadians(45.0)
                val tipX = center.x + radius * kotlin.math.cos(tipAngle).toFloat()
                val tipY = center.y + radius * kotlin.math.sin(tipAngle).toFloat()
                val headLength = size.minDimension * 0.18f
                val arrowHead = Path().apply {
                    moveTo(tipX, tipY - headLength / 2f)
                    lineTo(tipX + headLength * 0.7f, tipY)
                    lineTo(tipX, tipY + headLength / 2f)
                    close()
                }
                drawPath(arrowHead, color = tint)
            }
            Text(
                text = "5",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = tint
            )
        }
    }
}

/**
 * Morphs its own glyph between the pause bars and the play triangle rather than crossfading
 * between two separate icons -- the same technique used by Android's own animated play/pause
 * icon. Both shapes are drawn as two 4-point quadrilaterals; because the vertex *count* never
 * changes, lerping each vertex's position between the "bars" layout and the "triangle-half"
 * layout reads as the bars sweeping and merging into the triangle's tip (and back), instead of
 * one shape fading out while another fades in.
 */
@Composable
private fun MorphingPlayPauseIcon(isPlaying: Boolean, tint: Color, modifier: Modifier = Modifier) {
    // t=0 is the pause bars (shown while playing -- tapping pauses), t=1 is the play triangle
    // (shown while paused -- tapping plays). This is the same mapping the plain Icon ternary
    // used before (Pause while isPlaying, PlayArrow otherwise); it's easy to get backwards since
    // the *shape* named "Pause" is the one associated with isPlaying == true, not false.
    val morphProgress = remember { Animatable(if (isPlaying) 0f else 1f) }
    LaunchedEffect(isPlaying) {
        morphProgress.animateTo(
            targetValue = if (isPlaying) 0f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)

        // Pause: two full bars. Play: same two quads, each collapsing into half the triangle.
        val quad1Pause = listOf(p(4f, 3f), p(10f, 3f), p(10f, 21f), p(4f, 21f))
        val quad1Play = listOf(p(7f, 3f), p(7f, 12f), p(19f, 12f), p(19f, 12f))
        val quad2Pause = listOf(p(20f, 3f), p(20f, 21f), p(14f, 21f), p(14f, 3f))
        val quad2Play = listOf(p(19f, 12f), p(19f, 12f), p(7f, 21f), p(7f, 12f))

        val t = morphProgress.value
        fun lerp(a: Offset, b: Offset) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

        listOf(
            quad1Pause.zip(quad1Play) { a, b -> lerp(a, b) },
            quad2Pause.zip(quad2Play) { a, b -> lerp(a, b) }
        ).forEach { quad ->
            val path = Path().apply {
                moveTo(quad[0].x, quad[0].y)
                for (i in 1 until quad.size) lineTo(quad[i].x, quad[i].y)
                close()
            }
            drawPath(path, color = tint)
        }
    }
}

/**
 * Repeat's three states as two independently faded/scaled layers rather than three separate
 * icons: a "1" digit (visible in OFF and REPEAT_ONE) and a ring of arrows (visible in REPEAT_ONE
 * and REPEAT_ALL, reusing the plain Repeat glyph). OFF -> ONE grows the ring in around the digit;
 * ONE -> ALL fades the digit out, leaving the ring; ALL -> OFF fades the ring back out while the
 * digit fades back in.
 */
@Composable
private fun RepeatModeIcon(repeatMode: Int, tint: Color) {
    val ringVisible = repeatMode != Player.REPEAT_MODE_OFF
    val digitVisible = repeatMode != Player.REPEAT_MODE_ALL
    val ringScale by animateFloatAsState(if (ringVisible) 1f else 0.4f, label = "repeat_ring_scale")
    val ringAlpha by animateFloatAsState(if (ringVisible) 1f else 0f, label = "repeat_ring_alpha")
    val digitScale by animateFloatAsState(if (digitVisible) 1f else 0.4f, label = "repeat_digit_scale")
    val digitAlpha by animateFloatAsState(if (digitVisible) 1f else 0f, label = "repeat_digit_alpha")

    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.Repeat,
            contentDescription = "Repeat",
            tint = tint,
            modifier = Modifier.graphicsLayer {
                alpha = ringAlpha
                scaleX = ringScale
                scaleY = ringScale
            }
        )
        Text(
            text = "1",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = tint,
            modifier = Modifier.graphicsLayer {
                alpha = digitAlpha
                scaleX = digitScale
                scaleY = digitScale
            }
        )
    }
}

/**
 * A speaker glyph with 1, 2, or 3 bars, hand-drawn rather than picked from Material's icon set --
 * none of the built-in volume icons split cleanly into three tiers (VolumeDown/VolumeUp only
 * really give a two-way split). Each bar fades in/out independently on its own
 * [animateFloatAsState], so crossing a threshold (e.g. 30 -> 31%) grows or fades that bar in
 * smoothly instead of the icon just snapping to a different glyph.
 */
@Composable
private fun VolumeLevelIcon(level: Int, tint: Color, modifier: Modifier = Modifier) {
    val bar1Alpha by animateFloatAsState(if (level >= 1) 1f else 0.25f, label = "vol_bar1")
    val bar2Alpha by animateFloatAsState(if (level >= 2) 1f else 0.25f, label = "vol_bar2")
    val bar3Alpha by animateFloatAsState(if (level >= 3) 1f else 0.25f, label = "vol_bar3")

    Canvas(modifier = modifier.size(30.dp)) {
        val w = size.width
        val h = size.height

        // The speaker cone: a small square (the driver) merged with a trapezoid (the flare),
        // traced as one closed path -- the classic volume-icon silhouette.
        val cone = Path().apply {
            moveTo(w * 0.12f, h * 0.38f)
            lineTo(w * 0.30f, h * 0.38f)
            lineTo(w * 0.46f, h * 0.20f)
            lineTo(w * 0.46f, h * 0.80f)
            lineTo(w * 0.30f, h * 0.62f)
            lineTo(w * 0.12f, h * 0.62f)
            close()
        }
        drawPath(cone, color = tint)

        val center = Offset(w * 0.46f, h * 0.5f)
        val barAlphas = listOf(bar1Alpha, bar2Alpha, bar3Alpha)
        val strokeWidth = w * 0.05f
        barAlphas.forEachIndexed { index, alpha ->
            val radius = w * (0.14f + index * 0.10f)
            drawArc(
                color = tint.copy(alpha = alpha),
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Synced lyrics, auto-scrolling to whichever line is current and highlighting it. Tapping any
 * line jumps playback there. Isolated from [NowPlayingSheet] for the same reason as
 * [SeekSection] -- this polls the playback position on its own timer rather than the sheet
 * recomposing four times a second.
 */
@Composable
private fun LyricsView(
    lines: List<LyricLine>,
    positionProvider: () -> Long,
    onSeekTo: (Long) -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var activeIndex by remember(lines) { mutableIntStateOf(-1) }

    // Same 250ms cadence as the rest of the app's playback-position polling (see
    // MusicViewModel's own position ticker) -- no need to poll faster than lyric lines change.
    LaunchedEffect(lines) {
        while (true) {
            val position = positionProvider()
            // Last line whose timestamp has already passed -- i.e. the currently active one.
            val index = lines.indexOfLast { it.timestampMs <= position }
            if (index != activeIndex) {
                activeIndex = index
                if (index >= 0) {
                    // Keeps the active line roughly centered rather than pinned to the top.
                    listState.animateScrollToItem(index, scrollOffset = -200)
                }
            }
            delay(250)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            Text(
                text = line.text,
                style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = textColor.copy(alpha = if (isActive) 1f else 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekTo(line.timestampMs) }
                    .padding(vertical = 10.dp, horizontal = 8.dp)
            )
        }
    }
}

/**
 * Embedded, unsynced lyrics -- just a static scrollable block. No per-line timestamps means no
 * auto-scroll and nothing to seek to, unlike [LyricsView].
 */
@Composable
private fun PlainLyricsView(text: String, textColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 32.dp)
        )
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

package com.vui.vaporwave.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs

private fun approxEquals(a: Float, b: Float, epsilon: Float = 0.01f) = abs(a - b) < epsilon

@Composable
fun EffectsScreen(
    currentSpeed: Float,
    currentPitch: Float,
    isSlowedAndReverb: Boolean,
    onSetSpeedAndPitch: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Local drag state gives the slider immediate visual feedback while dragging, without
    // flooding ExoPlayer's Sonic audio processor with a playbackParameters update on every
    // touch frame -- the controller is only touched once the drag settles.
    var speedDrag by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    var pitchDrag by remember(currentPitch) { mutableFloatStateOf(currentPitch) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Preset Quick Action Buttons -- thin pills sitting directly next to the title (not
        // pushed to the row's trailing edge), each exactly as wide as the "Presets" text itself.
        var titleWidthPx by remember { mutableIntStateOf(0) }
        val titleWidthDp = with(LocalDensity.current) { titleWidthPx.toDp() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.onGloballyPositioned { titleWidthPx = it.size.width }
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Preset 1: Vaporwave Classic
                CompactPresetButton(
                    value = "0.85x",
                    width = titleWidthDp,
                    selected = approxEquals(currentSpeed, 0.85f) && approxEquals(currentPitch, 0.85f),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = { onSetSpeedAndPitch(0.85f, 0.85f) }
                )

                // Preset 3: Normal 1.0x
                CompactPresetButton(
                    value = "1x",
                    width = titleWidthDp,
                    selected = approxEquals(currentSpeed, 1.0f) && approxEquals(currentPitch, 1.0f),
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    onClick = { onSetSpeedAndPitch(1.0f, 1.0f) }
                )

                // Preset 2: Nightcore Sped Up
                CompactPresetButton(
                    value = "1.25x",
                    width = titleWidthDp,
                    selected = approxEquals(currentSpeed, 1.25f) && approxEquals(currentPitch, 1.25f),
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    onClick = { onSetSpeedAndPitch(1.25f, 1.25f) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Custom Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Playback Speed Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Playback Tempo / Speed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isSpeedAtDefault = approxEquals(speedDrag, 1.0f)
                        // Alpha/scale animated in place via graphicsLayer (a draw-phase transform)
                        // rather than AnimatedVisibility's default expand/shrink -- the IconButton
                        // always occupies its 28dp of layout space, so the Row's width never
                        // changes as the icon fades in/out, which is what was causing the tile to
                        // visibly judder-resize on every appear/disappear.
                        val resetAlpha by animateFloatAsState(
                            targetValue = if (isSpeedAtDefault) 0f else 1f,
                            label = "speed_reset_alpha"
                        )
                        val resetScale by animateFloatAsState(
                            targetValue = if (isSpeedAtDefault) 0.6f else 1f,
                            label = "speed_reset_scale"
                        )
                        IconButton(
                            onClick = { onSetSpeedAndPitch(1.0f, pitchDrag) },
                            enabled = !isSpeedAtDefault,
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    alpha = resetAlpha
                                    scaleX = resetScale
                                    scaleY = resetScale
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset speed to 1.00x",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2fx", speedDrag),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Slider(
                    value = speedDrag,
                    onValueChange = { speedDrag = it },
                    onValueChangeFinished = { onSetSpeedAndPitch(speedDrag, pitchDrag) },
                    valueRange = 0.5f..2.0f,
                    steps = 29, // 0.05 increments
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Playback Pitch Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tone & Pitch Shifter",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isPitchAtDefault = approxEquals(pitchDrag, 1.0f)
                        val resetAlpha by animateFloatAsState(
                            targetValue = if (isPitchAtDefault) 0f else 1f,
                            label = "pitch_reset_alpha"
                        )
                        val resetScale by animateFloatAsState(
                            targetValue = if (isPitchAtDefault) 0.6f else 1f,
                            label = "pitch_reset_scale"
                        )
                        IconButton(
                            onClick = { onSetSpeedAndPitch(speedDrag, 1.0f) },
                            enabled = !isPitchAtDefault,
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    alpha = resetAlpha
                                    scaleX = resetScale
                                    scaleY = resetScale
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset pitch to 1.00x",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2fx", pitchDrag),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Slider(
                    value = pitchDrag,
                    onValueChange = { pitchDrag = it },
                    onValueChangeFinished = { onSetSpeedAndPitch(speedDrag, pitchDrag) },
                    valueRange = 0.5f..1.5f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary
                    )
                )
            }
        }
    }
}

/**
 * A thin preset pill, exactly [width] wide (matching the "Presets" title) and a fixed short
 * height -- built on a plain [Surface] rather than M3's [Button], since Button enforces a 58dp
 * minimum width via `defaultMinSize` that a narrower explicit width can't override.
 */
@Composable
private fun CompactPresetButton(
    value: String,
    width: Dp,
    selected: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .height(20.dp),
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) containerColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) contentColor else MaterialTheme.colorScheme.onSurface
    ) {
        // fillMaxSize -- without it, this Box only wraps the Text's own size instead of the
        // Surface's full bounds, so contentAlignment = Center had nothing to center within and
        // the text sat wherever the Surface's own default placement put it.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

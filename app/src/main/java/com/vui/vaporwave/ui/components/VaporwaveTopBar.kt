package com.vui.vaporwave.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vui.vaporwave.ui.AppDestination
import com.vui.vaporwave.ui.LibraryTab
import kotlin.math.roundToInt

/**
 * A from-scratch top bar rather than Material3's [androidx.compose.material3.TopAppBar]: that
 * composable enforces a fixed height and clips overflow, which cuts off the title once the
 * one-handed reachability pull scales it up. A plain [Surface] + [Row] instead grows naturally
 * with its content, so nothing gets clipped as the pull progresses.
 */
@Composable
fun VaporwaveTopBar(
    title: String,
    showSwipeHint: Boolean,
    currentLibraryTab: LibraryTab,
    destination: AppDestination,
    onSearchClick: () -> Unit,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    pullProgress: Float = 0f
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    // Gap above the title (status bar to title). Independent of titleToDotsGap below --
    // they were briefly a single shared value so the two matched exactly, but tuning one by
    // itself (shift the title down, then pull the dots in closer) kept fighting the other
    // through that shared term, so they're now two separate, freely adjustable numbers.
    val titleTopGap = 3.dp + 4.dp * pullProgress

    // Gap between the title and the dots row below it.
    val titleToDotsGap = 2.dp + 4.dp * pullProgress

    // A fixed extra push for the title+dots block specifically -- NOT applied to the outer
    // Row's padding, which would also drag the search/settings icons down with it (they used
    // to move every time this got tuned, since they share that Row's CenterVertically
    // alignment). Applied instead as a Spacer inside the title's own Column, with the icons
    // separately pinned to Alignment.Top so they're structurally independent of whatever height
    // that Column ends up with. The outer Row's bottom padding is reduced by the same amount so
    // the bar's total height doesn't grow -- the title+dots block just sits lower within it.
    val titleExtraDownShift = 8.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = 12.dp,
                    end = 2.dp,
                    top = titleTopGap,
                    bottom = 16.dp + 6.dp * pullProgress - titleExtraDownShift
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // No longer swaps in a search field: search is its own page now. That also retires a
            // recurring bug, since an AnimatedContent resizing around the pull-scaled title kept
            // clipping it -- graphicsLayer scales at draw time without growing the layout, so any
            // animating, clipping container sized itself to the small unscaled title.
            Box(modifier = Modifier.weight(1f)) {
                Column {
                    Spacer(modifier = Modifier.height(titleExtraDownShift))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The pull-driven enlargement is done by growing fontSize itself, not by
                        // a graphicsLayer scale. graphicsLayer scales at draw time without
                        // changing layout size, so the surrounding layout never knew the title
                        // had grown -- the enlarged text either got clipped by the top bar's own
                        // Surface (when the scale pivot pointed up, into a few fixed px of
                        // padding) or drawn straight over the dots below (pivot pointing down,
                        // since there was never any real space reserved for the growth either
                        // way). Animating the real fontSize means the Row/Column actually
                        // measures the bigger text and the dots get pushed down to match, same
                        // as any other size change in a normal layout.
                        // Growth is quantized into a fixed number of steps rather than tracking
                        // pullProgress continuously. fontSize has to genuinely grow (not just a
                        // graphicsLayer scale -- see above) so the layout actually reserves space
                        // and pushes the dots down, but changing a Text's fontSize forces Skia to
                        // re-shape and re-measure the glyph run from scratch, and pullProgress
                        // updates on every touch-move frame (up to 120Hz) during the drag.
                        // Text is a skippable composable: if the fontSize value it receives is
                        // identical to last frame's, Compose skips re-measuring it entirely. 38
                        // steps across the full pull range (under 1sp per step) reads as smooth
                        // continuous growth while still cutting reshape work from once-per-frame
                        // (up to 120 times) down to at most 38 over the whole drag. 12 and 28 were
                        // tried first and the stepping was visible -- this is the next notch up.
                        val titleFontSizeSteps = 38
                        val quantizedPullProgress =
                            (pullProgress * titleFontSizeSteps).roundToInt() / titleFontSizeSteps.toFloat()
                        val titleFontSize = 25.sp * (1f + quantizedPullProgress * 0.9f)
                        // Plain instant swap, no animation: this bar "grows naturally with its
                        // content" (no fixed height), so any per-frame animation here forces
                        // Scaffold to remeasure the top bar every one of those frames, which
                        // gates layout of everything below it -- that measure cost was landing on
                        // the same frames the pager needs for its own swipe-settle animation on
                        // every tab change, reading as the swipe stalling until the title
                        // finished. A plain Text has no animation frames to compete with it.
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = titleFontSize,
                            letterSpacing = 1.sp,
                            maxLines = 1
                        )
                        if (showSwipeHint) {
                            Spacer(modifier = Modifier.width(6.dp))
                            SwipeHintIcon()
                        }
                    }
                    if (destination == AppDestination.LIBRARY) {
                        // titleToDotsGap directly -- the dots row no longer carries its own top
                        // padding (moved to bottom only), so this Spacer is the whole gap rather
                        // than needing to subtract a hidden extra amount from it.
                        Spacer(modifier = Modifier.height(titleToDotsGap))
                        LibrarySectionDots(
                            currentTab = currentLibraryTab,
                            pullProgress = pullProgress,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }
                }
            }

            if (destination == AppDestination.LIBRARY) {
                IconButton(onClick = onSearchClick, modifier = Modifier.align(Alignment.Top)) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
            }

            Box(modifier = Modifier.align(Alignment.Top)) {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Menu")
                }

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Library") },
                        leadingIcon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onDestinationSelected(AppDestination.LIBRARY)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Files") },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onDestinationSelected(AppDestination.FILES)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("FX Studio") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onDestinationSelected(AppDestination.EFFECTS)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onDestinationSelected(AppDestination.SETTINGS)
                        }
                    )
                }
            }
        }
    }
}

/**
 * A gently pulsing bidirectional-arrows icon shown next to the section title to hint
 * that the content area can be swiped left/right, until the user discovers it once.
 */
@Composable
private fun SwipeHintIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "swipeHintIcon")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swipeHintAlpha"
    )

    Icon(
        imageVector = Icons.Default.SwapHoriz,
        contentDescription = "Swipe left or right to browse",
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .size(20.dp)
            .graphicsLayer { this.alpha = alpha }
    )
}

/**
 * Dot page indicator for the five library sections, mirroring the current swipe position.
 */
@Composable
private fun LibrarySectionDots(currentTab: LibraryTab, modifier: Modifier = Modifier, pullProgress: Float = 0f) {
    val tabs = remember { LibraryTab.entries.toList() }
    // Dots grow modestly with the pull -- noticeable, but nowhere near as large as the title.
    val pullScale = 1f + pullProgress * 0.5f

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == currentTab
            val baseSize = if (isSelected) 5.5.dp else 3.5.dp
            // Held as State and read inside the layout/draw lambdas below rather than unwrapped
            // with `by` here. Read in composition scope, each animation frame recomposed and
            // relaid out all five dots -- and they animate during exactly the tab change the
            // title crossfade runs in. Deferring the reads keeps the layout pixel-identical
            // while dropping the per-frame recomposition entirely.
            val dotSize = animateDpAsState(targetValue = baseSize * pullScale, label = "dotSize")
            val dotColor = animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val side = dotSize.value.roundToPx().coerceAtLeast(0)
                        val placeable = measurable.measure(Constraints.fixed(side, side))
                        layout(side, side) { placeable.place(0, 0) }
                    }
                    .drawBehind {
                        drawCircle(color = dotColor.value)
                    }
            )
        }
    }
}

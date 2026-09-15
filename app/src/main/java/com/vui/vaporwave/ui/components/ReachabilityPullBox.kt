package com.vui.vaporwave.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * One-handed "reachability" gesture: once content is scrolled to the top, pulling further down
 * doesn't bounce it -- it reports progress upward via [onPullProgressChanged] so the caller
 * (MainActivity's top bar) can shift the title down/enlarge it for easier one-handed reach.
 *
 * [pullProgress]/[onPullProgressChanged] are a fully hoisted (controlled) pair, not local state:
 * this composable holds no memory of the pull amount itself, only reads the caller's current
 * value and reports changes back. That's deliberate -- MainActivity owns a single shared value
 * for the whole app, so a pull triggered on one destination (or one Library tab) is still in
 * effect after switching to another, rather than each screen/tab tracking (and losing) its own
 * independent copy the moment it's navigated away from and recomposed fresh.
 *
 * [isPagerScrollInProgress] lets a caller that hosts its own horizontal pager (LibraryScreen's
 * tab swiper) suppress this gesture while that pager's own swipe/settle animation is running --
 * irrelevant (defaults to never-scrolling) for callers with no pager of their own.
 *
 * [isLocked] forces the pull to stay fully open regardless of scrolling -- set when the pull was
 * opened via the Now Playing sheet's one-handed mode toggle rather than this gesture, so a
 * locked-open pull behaves like a deliberate mode switch (only the same toggle closes it) instead
 * of the usual "scroll away and it retracts" gesture behaviour. Read-only here: this composable
 * never sets it itself, only the toggle in MainActivity does.
 */
@Composable
fun ReachabilityPullBox(
    pullProgress: Float,
    onPullProgressChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPagerScrollInProgress: () -> Boolean = { false },
    isLocked: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val reachabilityMaxPullPx = with(LocalDensity.current) { 96.dp.toPx() }
    // rememberUpdatedState for everything read from inside the connection below: it's remembered
    // once (no restart keys) so it'd otherwise close over the very first composition's values
    // forever instead of staying current across recomposition.
    val currentPullPx by rememberUpdatedState(pullProgress * reachabilityMaxPullPx)
    val currentOnPullProgressChanged by rememberUpdatedState(onPullProgressChanged)
    val currentIsPagerScrollInProgress by rememberUpdatedState(isPagerScrollInProgress)
    val currentIsLocked by rememberUpdatedState(isLocked)

    // Snap open once pulled past 30% of the max, otherwise spring back.
    suspend fun settleReachabilityPull() {
        val current = currentPullPx
        if (current > 0f) {
            val target = if (current > reachabilityMaxPullPx * 0.3f) {
                reachabilityMaxPullPx
            } else {
                0f
            }
            animate(initialValue = current, targetValue = target) { value, _ ->
                currentOnPullProgressChanged(value / reachabilityMaxPullPx)
            }
        }
    }

    val reachabilityConnection = remember {
        object : NestedScrollConnection {
            // Scrolling further up (away from the top) retracts an open pull first, before the
            // content itself scrolls -- this is the only way to dismiss reachability mode, unless
            // it's locked (opened via the Now Playing toggle), in which case only that same
            // toggle can close it.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Never touch the pull while a horizontal page swipe is in progress -- doing so
                // was fighting the pager's own gesture handling and breaking its transitions.
                if (currentIsPagerScrollInProgress()) return Offset.Zero
                if (currentIsLocked) return Offset.Zero
                val current = currentPullPx
                if (current <= 0f || available.y >= 0f) return Offset.Zero
                val newPull = (current + available.y).coerceAtLeast(0f)
                val delta = newPull - current
                currentOnPullProgressChanged(newPull / reachabilityMaxPullPx)
                return Offset(0f, delta)
            }

            // Once the content can't consume any more downward drag (already at the top), extend
            // the pull instead of letting it bounce.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (currentIsPagerScrollInProgress()) return Offset.Zero
                if (available.y <= 0f) return Offset.Zero
                val current = currentPullPx
                val newPull = (current + available.y).coerceAtMost(reachabilityMaxPullPx)
                if (newPull == current) return Offset.Zero
                val delta = newPull - current
                currentOnPullProgressChanged(newPull / reachabilityMaxPullPx)
                return Offset(0f, delta)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Snap open (stays pulled down for one-handed reach) once pulled far enough;
                // otherwise spring back. Once open, it only closes when the user scrolls the
                // content back up (handled by onPreScroll above), not merely by releasing.
                // Skip while the pager is still settling its own horizontal swipe -- this was
                // also firing on that fling, causing a visible hitch right as the new page
                // finished sliding in.
                if (!currentIsPagerScrollInProgress()) {
                    settleReachabilityPull()
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier.nestedScroll(reachabilityConnection),
        content = content
    )
}

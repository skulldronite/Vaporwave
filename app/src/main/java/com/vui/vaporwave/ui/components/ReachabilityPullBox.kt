package com.vui.vaporwave.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * One-handed "reachability" gesture: once content is scrolled to the top, pulling further down
 * doesn't bounce it -- it reports progress upward via [onPullProgressChanged] so the caller
 * (MainActivity's top bar) can shift the title down/enlarge it for easier one-handed reach. Also
 * openable from anywhere in the content via a two-finger drag, not just once scrolled to the top.
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
 */
@Composable
fun ReachabilityPullBox(
    pullProgress: Float,
    onPullProgressChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPagerScrollInProgress: () -> Boolean = { false },
    content: @Composable BoxScope.() -> Unit
) {
    val reachabilityMaxPullPx = with(LocalDensity.current) { 96.dp.toPx() }
    // True only while the pull is open AND the two-finger gesture is what opened (or most
    // recently re-affirmed) it. Gates the single-finger swipe-up-to-close behaviour below: once
    // set, an ordinary one-finger scroll no longer retracts the pull, so it can only be
    // dismissed by the same two-finger gesture that opened it. Deliberately local (not hoisted
    // like the pull amount itself): it's a short-lived gesture-recognition detail, not state
    // worth carrying across a destination switch.
    var pullOpenedByTwoFingerGesture by remember { mutableStateOf(false) }
    // rememberUpdatedState for everything read from inside the connection/pointerInput below:
    // both are remembered once (no restart keys) so they'd otherwise close over the very first
    // composition's values forever instead of staying current across recomposition.
    val currentPullPx by rememberUpdatedState(pullProgress * reachabilityMaxPullPx)
    val currentOnPullProgressChanged by rememberUpdatedState(onPullProgressChanged)
    val currentIsPagerScrollInProgress by rememberUpdatedState(isPagerScrollInProgress)

    // Shared by both ways of ending a pull gesture (single-finger fling at the top, and the
    // two-finger drag below): snap open once pulled past 30% of the max, otherwise spring back.
    suspend fun settleReachabilityPull(viaTwoFinger: Boolean = false) {
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
            // Ending closed always clears the flag; ending open records whichever gesture just
            // settled it there.
            pullOpenedByTwoFingerGesture = viaTwoFinger && target == reachabilityMaxPullPx
        }
    }

    val reachabilityConnection = remember {
        object : NestedScrollConnection {
            // Scrolling further up (away from the top) retracts an open pull first, before the
            // content itself scrolls -- this is the only way to dismiss reachability mode,
            // unless it was opened by the two-finger gesture, in which case a plain one-finger
            // scroll must not be able to close it (only another two-finger drag can).
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Never touch the pull while a horizontal page swipe is in progress -- doing so
                // was fighting the pager's own gesture handling and breaking its transitions.
                if (currentIsPagerScrollInProgress()) return Offset.Zero
                if (pullOpenedByTwoFingerGesture) return Offset.Zero
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
        modifier = modifier
            .nestedScroll(reachabilityConnection)
            // Two-finger drag activates reachability from anywhere in the content, not just once
            // scrolled to the top -- the one-handed benefit (bringing the title bar, dots and
            // search/settings buttons into thumb reach) is just as useful mid-content. A
            // single-finger drag can't be reused for this: it already means "scroll toward
            // earlier content" everywhere except the top boundary. A second finger is
            // unambiguous and never collides with normal scrolling.
            .pointerInput(Unit) {
                // Deliberately not awaitEachGesture: its block runs on a restricted coroutine
                // scope that can only call other pointer-input suspend functions, and
                // settleReachabilityPull() (a plain suspend fun using animate()) isn't one of
                // those. awaitPointerEventScope carries the same restriction only within its own
                // block, so the settle call is placed after it returns, back on this
                // (unrestricted) PointerInputScope.
                while (true) {
                    awaitPointerEventScope {
                        var event: PointerEvent
                        // Declared outside the loop so the exit condition can reuse the count
                        // gathered below instead of walking the changes again.
                        var pressedCount: Int
                        do {
                            // PointerEventPass.Initial runs top-down, before the scrollable
                            // content's own gesture sees the event -- consuming here (once 2+
                            // pointers are down) hides these pointers from it entirely, so it
                            // never also scrolls from the same two fingers.
                            event = awaitPointerEvent(pass = PointerEventPass.Initial)

                            // Indexed loops rather than filter/sumOf/any. This runs for every
                            // pointer event on the page -- overwhelmingly single-finger scrolls
                            // and pager swipes that this gesture ignores -- so the collection
                            // operators were allocating a list per event (plus boxing in sumOf)
                            // on the touch path at 120Hz, purely to discover there was only one
                            // finger down.
                            val changes = event.changes
                            pressedCount = 0
                            var deltaSum = 0f
                            for (i in changes.indices) {
                                val change = changes[i]
                                if (change.pressed) {
                                    pressedCount++
                                    deltaSum += change.positionChange().y
                                }
                            }

                            if (pressedCount < 2 || currentIsPagerScrollInProgress()) continue
                            val avgDeltaY = deltaSum / pressedCount
                            if (avgDeltaY == 0f) continue
                            val current = currentPullPx
                            val newPull = (current + avgDeltaY).coerceIn(0f, reachabilityMaxPullPx)
                            if (newPull != current) {
                                currentOnPullProgressChanged(newPull / reachabilityMaxPullPx)
                            }
                            for (i in changes.indices) {
                                val change = changes[i]
                                if (change.pressed) change.consume()
                            }
                        } while (pressedCount > 0)
                    }
                    settleReachabilityPull(viaTwoFinger = true)
                }
            },
        content = content
    )
}

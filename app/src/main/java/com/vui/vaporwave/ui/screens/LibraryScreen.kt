package com.vui.vaporwave.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.vui.vaporwave.model.AlbumSummary
import com.vui.vaporwave.model.ArtistSummary
import com.vui.vaporwave.model.AudioTrack
import com.vui.vaporwave.model.Playlist
import com.vui.vaporwave.ui.LibraryTab
import com.vui.vaporwave.ui.SpotlightCategory
import com.vui.vaporwave.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val ALPHABET_SCROLLBAR_WIDTH = 28.dp
private val PRECISE_SCROLLBAR_WIDTH = 20.dp
// Extra breathing room from the screen edge so the precise scrollbar's touch area doesn't
// overlap the system's edge-swipe-back gesture zone on devices with fullscreen gesture nav.
private val PRECISE_SCROLLBAR_EDGE_INSET = 10.dp

enum class LibrarySortOption(val label: String, val icon: ImageVector) {
    NAME("Name", Icons.Default.SortByAlpha),
    DATE_ADDED("Date Added", Icons.Default.Schedule),
    ARTIST("Artist", Icons.Default.Person),
    TRACK_NUMBER("Track Number", Icons.Default.FormatListNumbered)
}

@Composable
fun LibraryScreen(
    tracks: List<AudioTrack>,
    allTracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    searchQuery: String,
    favouriteTracks: List<AudioTrack>,
    albums: List<AlbumSummary>,
    artists: List<ArtistSummary>,
    playlists: List<Playlist>,
    recentlyAddedTracks: List<AudioTrack>,
    mostPlayedTracks: List<AudioTrack>,
    recentlyPlayedTracks: List<AudioTrack>,
    currentTab: LibraryTab,
    onTabChanged: (LibraryTab) -> Unit,
    onSwipeDetected: () -> Unit,
    onTrackClick: (AudioTrack, List<AudioTrack>) -> Unit,
    onOpenFileClick: () -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSpotlight: (SpotlightCategory) -> Unit,
    modifier: Modifier = Modifier,
    onPullProgressChanged: (Float) -> Unit = {},
    isOverlayOpen: Boolean = false
) {
    val tabs = remember { LibraryTab.entries.toList() }
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(currentTab)) { tabs.size }

    // Reachability gesture: once a list is scrolled to the top, pulling further down doesn't
    // bounce the list -- it reports progress upward so the UI can shift down/enlarge the title
    // for easier one-handed reach. A single connection here covers every list in every tab,
    // since nested scroll deltas bubble up through this composable regardless of which
    // LazyColumn is currently active inside the pager.
    val reachabilityMaxPullPx = with(LocalDensity.current) { 96.dp.toPx() }
    var reachabilityPullPx by remember { mutableFloatStateOf(0f) }
    // True only while the pull is open AND the two-finger gesture is what opened (or most
    // recently re-affirmed) it. Gates the single-finger swipe-up-to-close behaviour below: once
    // set, an ordinary one-finger scroll no longer retracts the pull, so it can only be
    // dismissed by the same two-finger gesture that opened it.
    var pullOpenedByTwoFingerGesture by remember { mutableStateOf(false) }
    // The connection below is remembered once, so it would otherwise capture the very first
    // onPullProgressChanged lambda forever and keep calling a stale one after recomposition.
    val currentOnPullProgressChanged by rememberUpdatedState(onPullProgressChanged)

    // Shared by both ways of ending a pull gesture (single-finger fling at the top, and the
    // two-finger drag below): snap open once pulled past 30% of the max, otherwise spring back.
    suspend fun settleReachabilityPull(viaTwoFinger: Boolean = false) {
        if (reachabilityPullPx > 0f) {
            val target = if (reachabilityPullPx > reachabilityMaxPullPx * 0.3f) {
                reachabilityMaxPullPx
            } else {
                0f
            }
            animate(initialValue = reachabilityPullPx, targetValue = target) { value, _ ->
                reachabilityPullPx = value
                currentOnPullProgressChanged(reachabilityPullPx / reachabilityMaxPullPx)
            }
            // Ending closed always clears the flag; ending open records whichever gesture just
            // settled it there.
            pullOpenedByTwoFingerGesture = viaTwoFinger && target == reachabilityMaxPullPx
        }
    }

    val reachabilityConnection = remember {
        object : NestedScrollConnection {
            // Scrolling further up (away from the top) retracts an open pull first, before the
            // list itself scrolls -- this is the only way to dismiss reachability mode, unless
            // it was opened by the two-finger gesture, in which case a plain one-finger scroll
            // must not be able to close it (only another two-finger drag can).
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Never touch the pull while a horizontal page swipe is in progress -- doing so
                // was fighting the pager's own gesture handling and breaking its transitions.
                if (pagerState.isScrollInProgress) return Offset.Zero
                if (pullOpenedByTwoFingerGesture) return Offset.Zero
                if (reachabilityPullPx <= 0f || available.y >= 0f) return Offset.Zero
                val newPull = (reachabilityPullPx + available.y).coerceAtLeast(0f)
                val delta = newPull - reachabilityPullPx
                reachabilityPullPx = newPull
                currentOnPullProgressChanged(reachabilityPullPx / reachabilityMaxPullPx)
                return Offset(0f, delta)
            }

            // Once the list can't consume any more downward drag (already at the top), extend
            // the pull instead of letting the list bounce.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (pagerState.isScrollInProgress) return Offset.Zero
                if (available.y <= 0f) return Offset.Zero
                val newPull = (reachabilityPullPx + available.y).coerceAtMost(reachabilityMaxPullPx)
                if (newPull == reachabilityPullPx) return Offset.Zero
                val delta = newPull - reachabilityPullPx
                reachabilityPullPx = newPull
                currentOnPullProgressChanged(reachabilityPullPx / reachabilityMaxPullPx)
                return Offset(0f, delta)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Snap open (stays pulled down for one-handed reach) once pulled far enough;
                // otherwise spring back. Once open, it only closes when the user scrolls the
                // list back up (handled by onPreScroll above), not merely by releasing.
                // Skip while the pager is still settling its own horizontal swipe -- this was
                // also firing on that fling (pagerState.isScrollInProgress stays true for the
                // whole settle animation), causing a visible hitch right as the new page
                // finished sliding in.
                if (!pagerState.isScrollInProgress) {
                    settleReachabilityPull()
                }
                return Velocity.Zero
            }
        }
    }

    var hasComposedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        onTabChanged(tabs[pagerState.currentPage])
        // Only treat this as a real page change (not the initial composition) so the
        // swipe hint doesn't get dismissed before the user actually swipes.
        if (hasComposedOnce) {
            onSwipeDetected()
        }
        hasComposedOnce = true
    }

    val currentTabShown = tabs.getOrNull(pagerState.currentPage)
    // The Tracks tab has no back stack of its own; without this, an accidental edge-swipe
    // (e.g. while reaching for the alphabet scrollbar) falls through and exits the app. Disabled
    // while a top-level overlay (search or a library detail) is open, since it sits underneath
    // and would otherwise swallow the back press meant to close that overlay before
    // MainActivity's own handler ever sees it.
    BackHandler(enabled = currentTabShown == LibraryTab.TRACKS && !isOverlayOpen) {}

    Column(modifier = modifier.fillMaxSize()) {
        // beyondViewportPageCount = 4 (all 5 tabs): keeps every tab composed permanently, never
        // disposed. A window of 1 was tried first, but it only delays disposal by one hop rather
        // than preventing it -- the window slides with you, so a normal explore-three-tabs-then-
        // return pattern (e.g. Tracks -> Playlists -> Artists -> Tracks) still evicts and
        // cold-rebuilds whichever tab falls furthest behind, which is exactly what was still
        // producing hitches. With only 5 tabs total, the honest way to guarantee every tab stays
        // warm is to keep all of them alive all the time. Trade-off: every tab starts composing
        // and loading its images at cold launch instead of as each is visited, and all 5 tabs'
        // worth of artwork stays resident in memory for the whole session -- offset somewhat by
        // the disk-cached ImageLoader (see VaporwaveApplication), which turns most of that
        // launch-time work into cache hits after the first cold start.
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 4,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // Attached per-page (not around the pager) so it only ever sees scroll deltas
            // bubbling from that page's own list -- fully isolated from the pager's own
            // horizontal swipe/settle gesture handling, which lives above this in the tree.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(reachabilityConnection)
                    // Two-finger drag activates reachability from anywhere in the list, not just
                    // once scrolled to the top -- the one-handed benefit (bringing the title bar,
                    // dots and search/settings buttons into thumb reach) is just as useful
                    // mid-list. A single-finger drag can't be reused for this: it already means
                    // "scroll toward earlier tracks" everywhere except the top boundary, which
                    // is the only place a single-finger downward drag has nothing left to
                    // consume. A second finger is unambiguous and never collides with normal
                    // scrolling.
                    .pointerInput(Unit) {
                        // Deliberately not awaitEachGesture: its block runs on a restricted
                        // coroutine scope that can only call other pointer-input suspend
                        // functions, and settleReachabilityPull() (a plain suspend fun using
                        // animate()) isn't one of those. awaitPointerEventScope carries the same
                        // restriction only within its own block, so the settle call is placed
                        // after it returns, back on this (unrestricted) PointerInputScope.
                        while (true) {
                            awaitPointerEventScope {
                                var event: PointerEvent
                                // Declared outside the loop so the exit condition can reuse the
                                // count gathered below instead of walking the changes again.
                                var pressedCount: Int
                                do {
                                    // PointerEventPass.Initial runs top-down, before the
                                    // LazyColumn's own scrollable gesture sees the event --
                                    // consuming here (once 2+ pointers are down) hides these
                                    // pointers from it entirely, so it never also scrolls from
                                    // the same two fingers.
                                    event = awaitPointerEvent(pass = PointerEventPass.Initial)

                                    // Indexed loops rather than filter/sumOf/any. This runs for
                                    // every pointer event on the page -- overwhelmingly
                                    // single-finger scrolls and pager swipes that this gesture
                                    // ignores -- so the collection operators were allocating a
                                    // list per event (plus boxing in sumOf) on the touch path at
                                    // 120Hz, purely to discover there was only one finger down.
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

                                    if (pressedCount < 2 || pagerState.isScrollInProgress) continue
                                    val avgDeltaY = deltaSum / pressedCount
                                    if (avgDeltaY == 0f) continue
                                    val newPull = (reachabilityPullPx + avgDeltaY)
                                        .coerceIn(0f, reachabilityMaxPullPx)
                                    if (newPull != reachabilityPullPx) {
                                        reachabilityPullPx = newPull
                                        currentOnPullProgressChanged(reachabilityPullPx / reachabilityMaxPullPx)
                                    }
                                    for (i in changes.indices) {
                                        val change = changes[i]
                                        if (change.pressed) change.consume()
                                    }
                                } while (pressedCount > 0)
                            }
                            settleReachabilityPull(viaTwoFinger = true)
                        }
                    }
            ) {
            when (tabs[page]) {
                LibraryTab.ALBUMS -> {
                    AlbumsList(
                        albums = albums,
                        onAlbumClick = onOpenAlbum
                    )
                }
                LibraryTab.FAVOURITES -> {
                    SimpleTrackList(
                        tracks = favouriteTracks,
                        currentTrack = currentTrack,
                        emptyIcon = Icons.Default.Favorite,
                        emptyMessage = "No favourites yet.\nTap the heart on Now Playing to add one.",
                        onTrackClick = { track -> onTrackClick(track, favouriteTracks) },
                        onShufflePlay = onShufflePlay
                    )
                }
                LibraryTab.TRACKS -> {
                    TracksList(
                        tracks = tracks,
                        currentTrack = currentTrack,
                        searchQuery = searchQuery,
                        onTrackClick = { track -> onTrackClick(track, tracks) },
                        onOpenFileClick = onOpenFileClick,
                        onShufflePlay = onShufflePlay
                    )
                }
                LibraryTab.PLAYLISTS -> {
                    PlaylistsList(
                        playlists = playlists,
                        onPlaylistClick = { onOpenPlaylist(it.id) },
                        recentlyAddedTrack = recentlyAddedTracks.firstOrNull(),
                        mostPlayedTrack = mostPlayedTracks.firstOrNull(),
                        recentlyPlayedTrack = recentlyPlayedTracks.firstOrNull(),
                        onCategoryClick = onOpenSpotlight
                    )
                }
                LibraryTab.ARTISTS -> {
                    ArtistsList(
                        artists = artists,
                        onArtistClick = onOpenArtist,
                        onShuffleClick = { onShufflePlay(allTracks) }
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun TracksList(
    tracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    searchQuery: String,
    onTrackClick: (AudioTrack) -> Unit,
    onOpenFileClick: () -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) "No tracks match '$searchQuery'" else "No tracks found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpenFileClick) {
                    Text("Pick Audio File from Device")
                }
            }
        }
    } else {
        var sortOption by remember { mutableStateOf(LibrarySortOption.NAME) }
        // Sorting runs off the main thread and keeps the previous ordering on screen until the
        // new one is ready, rather than blocking composition -- a several-thousand-track library
        // sorted synchronously here was landing squarely on the frames the pager needs to settle
        // its swipe animation on, which is what made switching tabs (this list is torn down and
        // re-sorted from scratch every time it's re-entered) read as janky/stuck.
        var sortedTracks by remember { mutableStateOf(tracks) }
        LaunchedEffect(tracks, sortOption) {
            sortedTracks = withContext(Dispatchers.Default) { sortTracks(tracks, sortOption) }
        }

        val listState = rememberLazyListState()
        val canScroll by remember {
            derivedStateOf { listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size }
        }
        val isAtTop by remember {
            derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
        }

        Column(modifier = modifier.fillMaxSize()) {
            // Only visible while scrolled to the very top; scrolling down hides it.
            AnimatedVisibility(visible = isAtTop) {
                LibraryToolbar(
                    sortOption = sortOption,
                    onSortOptionSelected = { sortOption = it },
                    onShuffleClick = { onShufflePlay(sortedTracks) },
                    availableSortOptions = listOf(LibrarySortOption.NAME, LibrarySortOption.ARTIST)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = 120.dp,
                        top = 4.dp,
                        end = if (canScroll) ALPHABET_SCROLLBAR_WIDTH else 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = sortedTracks,
                        key = { it.id }
                    ) { track ->
                        val isPlayingThis = currentTrack?.id == track.id
                        TrackItem(
                            track = track,
                            isPlayingThisTrack = isPlayingThis,
                            onClick = { onTrackClick(track) },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                if (canScroll) {
                    AlphabetScrollbar(
                        labels = remember(sortedTracks, sortOption) { trackLabelsFor(sortedTracks, sortOption) },
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

// CASE_INSENSITIVE_ORDER rather than sortedBy { it.lowercase() }: the selector in sortedBy runs
// on every comparison, so lowercasing there allocates O(n log n) throwaway strings for a sort
// that is otherwise allocation-free.
private fun sortTracks(tracks: List<AudioTrack>, sortOption: LibrarySortOption): List<AudioTrack> =
    when (sortOption) {
        LibrarySortOption.NAME -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        LibrarySortOption.DATE_ADDED -> tracks.sortedByDescending { it.dateAddedMs }
        LibrarySortOption.ARTIST -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
        // Untagged tracks (trackNumber == 0) sort after every numbered one rather than jumping
        // to the front.
        LibrarySortOption.TRACK_NUMBER -> tracks.sortedBy { if (it.trackNumber == 0) Int.MAX_VALUE else it.trackNumber }
    }

private fun trackLabelsFor(tracks: List<AudioTrack>, sortOption: LibrarySortOption): List<String> =
    if (sortOption == LibrarySortOption.ARTIST) tracks.map { it.artist } else tracks.map { it.title }

/**
 * Sticky toolbar above a list: a sort-option picker on the left, and a dice button on the right
 * that rolls through random faces, shuffles, and plays a random track from the current order.
 */
@Composable
private fun LibraryToolbar(
    sortOption: LibrarySortOption,
    onSortOptionSelected: (LibrarySortOption) -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
    availableSortOptions: List<LibrarySortOption> = LibrarySortOption.entries
) {
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TextButton(onClick = { isSortMenuExpanded = true }) {
                Icon(
                    imageVector = sortOption.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(sortOption.label, style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(
                expanded = isSortMenuExpanded,
                onDismissRequest = { isSortMenuExpanded = false }
            ) {
                availableSortOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = { Icon(option.icon, contentDescription = null) },
                        trailingIcon = {
                            if (option == sortOption) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            onSortOptionSelected(option)
                            isSortMenuExpanded = false
                        }
                    )
                }
            }
        }

        ShuffleDiceButton(onShuffleClick = onShuffleClick)
    }
}

/**
 * The dice icon button shared by every "shuffle play this list" affordance in the library: it
 * spins and rapidly cycles random faces before settling and actually triggering [onShuffleClick].
 */
@Composable
fun ShuffleDiceButton(
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val diceRotation = remember { Animatable(0f) }
    var diceFace by remember { mutableIntStateOf(1) }
    var isRolling by remember { mutableStateOf(false) }

    IconButton(
        enabled = !isRolling,
        onClick = {
            isRolling = true
            coroutineScope.launch {
                launch {
                    diceRotation.animateTo(
                        targetValue = diceRotation.value + 360f,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                    )
                }
                // Rapidly cycle random faces so the dice appears to actually roll a number.
                repeat(10) {
                    diceFace = (1..6).random()
                    delay(55)
                }
                isRolling = false
                onShuffleClick()
            }
        },
        modifier = modifier
    ) {
        DiceFace(
            value = diceFace,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { rotationZ = diceRotation.value }
        )
    }
}

/**
 * Draws an actual six-sided die face (rounded square + pip dots) for the given [value] (1-6),
 * rather than a printed digit.
 */
@Composable
private fun DiceFace(value: Int, modifier: Modifier = Modifier) {
    val faceColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = modifier) {
        val corner = size.minDimension * 0.22f
        drawRoundRect(color = faceColor, cornerRadius = CornerRadius(corner, corner))

        val dotRadius = size.minDimension * 0.09f
        val positions: List<Pair<Float, Float>> = when (value) {
            1 -> listOf(0.5f to 0.5f)
            2 -> listOf(0.28f to 0.28f, 0.72f to 0.72f)
            3 -> listOf(0.28f to 0.28f, 0.5f to 0.5f, 0.72f to 0.72f)
            4 -> listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.28f to 0.72f, 0.72f to 0.72f)
            5 -> listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.5f to 0.5f, 0.28f to 0.72f, 0.72f to 0.72f)
            else -> listOf(0.28f to 0.25f, 0.72f to 0.25f, 0.28f to 0.5f, 0.72f to 0.5f, 0.28f to 0.75f, 0.72f to 0.75f)
        }
        positions.forEach { (fx, fy) ->
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(fx * size.width, fy * size.height))
        }
    }
}

@Composable
private fun SimpleTrackList(
    tracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    emptyIcon: ImageVector,
    emptyMessage: String,
    onTrackClick: (AudioTrack) -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = emptyIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        var sortOption by remember { mutableStateOf(LibrarySortOption.NAME) }
        // See TracksList's identical comment: sorting off the main thread avoids blocking the
        // frame this list is re-composed and re-sorted on every time its tab is swiped back into.
        var sortedTracks by remember { mutableStateOf(tracks) }
        LaunchedEffect(tracks, sortOption) {
            sortedTracks = withContext(Dispatchers.Default) { sortTracks(tracks, sortOption) }
        }

        val listState = rememberLazyListState()
        val canScroll by remember {
            derivedStateOf { listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size }
        }
        val isAtTop by remember {
            derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
        }

        Column(modifier = modifier.fillMaxSize()) {
            AnimatedVisibility(visible = isAtTop) {
                LibraryToolbar(
                    sortOption = sortOption,
                    onSortOptionSelected = { sortOption = it },
                    onShuffleClick = { onShufflePlay(sortedTracks) },
                    availableSortOptions = listOf(LibrarySortOption.NAME, LibrarySortOption.ARTIST)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = 120.dp,
                        top = 4.dp,
                        end = if (canScroll) ALPHABET_SCROLLBAR_WIDTH else 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = sortedTracks,
                        key = { it.id }
                    ) { track ->
                        val isPlayingThis = currentTrack?.id == track.id
                        TrackItem(
                            track = track,
                            isPlayingThisTrack = isPlayingThis,
                            onClick = { onTrackClick(track) },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                if (canScroll) {
                    AlphabetScrollbar(
                        labels = remember(sortedTracks, sortOption) { trackLabelsFor(sortedTracks, sortOption) },
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

/** A centered hero square shown above the title on an album or artist detail page. */
data class HeroArtwork(val uri: Uri?, val placeholderIcon: ImageVector)

@Composable
fun DetailTrackList(
    title: String,
    subtitle: String,
    tracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    onBack: () -> Unit,
    onTrackClick: (AudioTrack) -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    modifier: Modifier = Modifier,
    showToolbar: Boolean = true,
    heroArtwork: HeroArtwork? = null,
    showTrackArtwork: Boolean = true,
    // Artist/playlist/spotlight keep the original three; album detail overrides this to just
    // Name + Track Number (see MainActivity), which also gates the scrollbars below.
    availableSortOptions: List<LibrarySortOption> = listOf(
        LibrarySortOption.NAME,
        LibrarySortOption.DATE_ADDED,
        LibrarySortOption.ARTIST
    )
) {
    var sortOption by remember { mutableStateOf(LibrarySortOption.NAME) }
    val sortedTracks = remember(tracks, sortOption, showToolbar) {
        if (showToolbar) sortTracks(tracks, sortOption) else tracks
    }

    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    val density = LocalDensity.current
    // Measured from the two actual header composables below (onSizeChanged) -- hero art and
    // toolbar are tracked separately since the toolbar is drawn in a different z-layer (see
    // below) to stay clickable, but their combined height is also the LazyColumn's top
    // contentPadding, so scrolling through that padding *is* what moves
    // firstVisibleItemScrollOffset -- dividing by that same total (not an unrelated constant) is
    // what keeps the header's translation and the list's own scroll 1:1 in lockstep: scroll
    // 10px, the header moves up exactly 10px, same as the list content below it.
    var heroSectionHeightPx by remember { mutableIntStateOf(0) }
    var toolbarSectionHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightPx by remember {
        derivedStateOf { heroSectionHeightPx + toolbarSectionHeightPx }
    }
    // Raw pixels scrolled since the top, clamped to the header's own height -- tracked directly
    // from real gesture deltas via the NestedScrollConnection below, rather than inferred from
    // listState.firstVisibleItemIndex/firstVisibleItemScrollOffset. Those two only describe
    // progress through the actual *items*, and how they account for a large top contentPadding
    // before item 0 turned out not to match a simple "offset / headerHeightPx" reading -- alpha
    // was reaching 0 well before the list had genuinely scrolled the full header height, which is
    // what made the header look like it just vanished partway through the gesture rather than
    // fading the whole way. Measuring the real scroll delta directly removes that guesswork.
    var pushedPx by remember { mutableFloatStateOf(0f) }
    val headerPushConnection = remember {
        object : NestedScrollConnection {
            // Pure observer -- always returns Offset.Zero, so this never changes how the list
            // itself scrolls (no risk of repeating the earlier "can't scroll" regression).
            //
            // Tracks onPostScroll's `consumed` (what the list actually scrolled), not
            // onPreScroll's `available` (the raw drag delta, which keeps growing past however
            // far the list can actually move -- e.g. once it's hit the bottom with nothing left
            // to reveal). Using `available` there meant a short album could still be dragged
            // past its own scroll limit and keep the header collapsing/pushing after the list
            // itself had already stopped moving. `consumed` is naturally zero once the list can't
            // scroll any further in that direction, so the header stops exactly when it does.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Dragging up (revealing further content) reports a negative Y delta here --
                // subtracting it increases how far "pushed" the header is. Dragging back down
                // reverses it the same way.
                pushedPx = (pushedPx - consumed.y).coerceIn(0f, headerHeightPx.toFloat())
                return Offset.Zero
            }
        }
    }
    // Continuous 0f (fully open) .. 1f (fully collapsed), driven directly by pushedPx -- not a
    // boolean threshold. AnimatedVisibility's isAtTop toggle used to drive this as a fade, which
    // (a) only ever faded in place rather than actually pushing the list up (fadeOut() alone
    // doesn't shrink the space the hero occupies) and (b) could snap back to visible the moment
    // isAtTop flickered true again, reading as an unwanted "reappear". Tying the fraction to a
    // continuously tracked value removes that snap entirely: there's no boolean to flicker, just
    // a value that tracks the actual scroll distance. The hero art and the toolbar below both
    // read this same fraction, so they fade and move by exactly the pushed amount, in lockstep
    // with each other.
    val heroCollapseFraction by remember {
        derivedStateOf {
            if (headerHeightPx <= 0) 0f else (pushedPx / headerHeightPx.toFloat()).coerceIn(0f, 1f)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (heroArtwork != null && tracks.isNotEmpty()) {
            // Header and list share one Box rather than stacking in the Column: the header used
            // to be a real layout sibling above the list whose *measured height* shrank as
            // heroCollapseFraction grew, which meant every scroll-delta frame also resized the
            // list's own container -- on at least one device that fed back into the list's own
            // scroll handling badly enough to read as "can't scroll at all". Here the header is
            // a purely visual overlay (graphicsLayer alpha/translation only, draw-phase, no
            // relayout) with zero influence on the list's size, so the list's layout is fully
            // static during scroll no matter what the header is doing.
            //
            // The two header pieces sit in different z-layers on purpose. The hero art (icon,
            // title, subtitle) is pure decoration, so it's drawn *before* the LazyColumn (under
            // it) -- a touch anywhere on it still reaches the list underneath for scrolling. The
            // toolbar (sort menu + shuffle dice) is the one interactive part, so it's drawn
            // *after* the LazyColumn (on top) so its buttons actually receive taps instead of
            // the list swallowing them; it still fades/translates by the exact same amount as
            // the hero art above it, so the two read as one continuous block regardless of
            // which layer either is in.
            // Only album detail offers Track Number as a sort option at all, so gating both
            // scrollbars on its presence keeps them out of artist/playlist/spotlight, where a
            // number pulled from file metadata (or an alphabetical jump by title) doesn't apply.
            // Otherwise the only thing that switches between them is which sort mode is active --
            // they're never additionally hidden for being "not scrollable enough".
            val tracksNumberable = availableSortOptions.contains(LibrarySortOption.TRACK_NUMBER)
            val showAlphabetScrollbar = tracksNumberable && sortOption == LibrarySortOption.NAME
            val showPreciseScrollbar = tracksNumberable && sortOption == LibrarySortOption.TRACK_NUMBER

            // Only used to decide *how* a scrollbar jump behaves below -- the scrollbars
            // themselves stay visible regardless (see the comment above).
            val canScroll by remember {
                derivedStateOf { listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size }
            }
            // When the list can't actually scroll, scrollToItem would still nudge it by whatever
            // sliver of range is technically left, and since that's a programmatic jump (not a
            // drag) it bypasses headerPushConnection entirely -- the header has no way to know it
            // happened, so it falls out of sync with the list. Highlighting the matching row
            // instead sidesteps that: nothing scrolls, so nothing can fall out of sync.
            var highlightedTrackId by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(highlightedTrackId) {
                if (highlightedTrackId != null) {
                    delay(900)
                    highlightedTrackId = null
                }
            }
            val onScrollbarJump: ((Int) -> Unit)? = if (canScroll) {
                null
            } else {
                { index -> highlightedTrackId = sortedTracks.getOrNull(index)?.id }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(headerPushConnection)
            ) {
                val headerHeightDp = with(density) { headerHeightPx.toDp() }
                val heroSectionHeightDp = with(density) { heroSectionHeightPx.toDp() }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .onSizeChanged { heroSectionHeightPx = it.height }
                        .graphicsLayer {
                            alpha = 1f - heroCollapseFraction
                            translationY = -heroCollapseFraction * headerHeightPx
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Room for the back button, which is pinned separately below (always on
                    // top, never collapsing) so it doesn't scroll away with the rest.
                    Spacer(modifier = Modifier.height(52.dp))
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = heroArtwork.placeholderIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        if (heroArtwork.uri != null) {
                            val context = LocalContext.current
                            AsyncImage(
                                model = remember(heroArtwork.uri) {
                                    ImageRequest.Builder(context)
                                        .data(heroArtwork.uri)
                                        .size(Size(360, 360))
                                        .build()
                                },
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = 120.dp,
                        top = headerHeightDp,
                        end = when {
                            showAlphabetScrollbar -> ALPHABET_SCROLLBAR_WIDTH
                            showPreciseScrollbar -> PRECISE_SCROLLBAR_WIDTH + PRECISE_SCROLLBAR_EDGE_INSET
                            else -> 0.dp
                        }
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = sortedTracks, key = { it.id }) { track ->
                        val isPlayingThis = currentTrack?.id == track.id
                        TrackItem(
                            track = track,
                            isPlayingThisTrack = isPlayingThis,
                            onClick = { onTrackClick(track) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                            showArtwork = showTrackArtwork,
                            // Only meaningful once the list is actually ordered by it -- shown
                            // under Name sort too it'd read as index-like clutter unrelated to
                            // the alphabetical order on screen.
                            trackNumber = if (sortOption == LibrarySortOption.TRACK_NUMBER) track.trackNumber else null,
                            isHighlighted = track.id == highlightedTrackId
                        )
                    }
                }

                if (showToolbar) {
                    // Drawn after the LazyColumn (on top) so it actually receives taps -- see
                    // the z-layer comment above. Offset down by the hero section's own measured
                    // height so it sits directly beneath it, and driven by the same
                    // heroCollapseFraction/headerHeightPx as the hero art so the two move and
                    // fade as a single unit.
                    LibraryToolbar(
                        sortOption = sortOption,
                        onSortOptionSelected = { sortOption = it },
                        onShuffleClick = { onShufflePlay(sortedTracks) },
                        availableSortOptions = availableSortOptions,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(0.9f)
                            .offset(y = heroSectionHeightDp + 8.dp)
                            .onSizeChanged { toolbarSectionHeightPx = it.height }
                            .graphicsLayer {
                                alpha = 1f - heroCollapseFraction
                                translationY = -heroCollapseFraction * headerHeightPx
                            }
                    )
                }

                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 4.dp, top = 4.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                if (showAlphabetScrollbar) {
                    AlphabetScrollbar(
                        labels = remember(sortedTracks) { trackLabelsFor(sortedTracks, LibrarySortOption.NAME) },
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            // Grows to fill the space the header just vacated as it collapses,
                            // rather than leaving a dead top gap where rows are now visible but
                            // no scrollbar covers them -- see the comment on PreciseScrollbar's
                            // identical modifier below for the mechanics.
                            .layout { measurable, constraints ->
                                val visibleHeaderPx = (headerHeightPx * (1f - heroCollapseFraction))
                                    .roundToInt().coerceIn(0, constraints.maxHeight)
                                val rowsHeight = constraints.maxHeight - visibleHeaderPx
                                val placeable = measurable.measure(
                                    constraints.copy(minHeight = rowsHeight, maxHeight = rowsHeight)
                                )
                                layout(placeable.width, constraints.maxHeight) {
                                    placeable.placeRelative(0, visibleHeaderPx)
                                }
                            },
                        onJump = onScrollbarJump
                    )
                }

                if (showPreciseScrollbar) {
                    PreciseScrollbar(
                        itemCount = sortedTracks.size,
                        listState = listState,
                        onJump = onScrollbarJump,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = PRECISE_SCROLLBAR_EDGE_INSET)
                            // This layer's own reported height always stays the full container
                            // height (constraints.maxHeight, unchanged frame to frame) -- only
                            // the *inner* measured content shrinks/grows and is repositioned by
                            // visibleHeaderPx. That keeps the outer BoxWithConstraints from ever
                            // being asked to re-measure as this reacts to scroll, the same class
                            // of feedback loop that broke scrolling the first time around (see
                            // the comment above on why the header itself is graphicsLayer-only).
                            .layout { measurable, constraints ->
                                val visibleHeaderPx = (headerHeightPx * (1f - heroCollapseFraction))
                                    .roundToInt().coerceIn(0, constraints.maxHeight)
                                val rowsHeight = constraints.maxHeight - visibleHeaderPx
                                val placeable = measurable.measure(
                                    constraints.copy(minHeight = rowsHeight, maxHeight = rowsHeight)
                                )
                                layout(placeable.width, constraints.maxHeight) {
                                    placeable.placeRelative(0, visibleHeaderPx)
                                }
                            }
                    )
                }
            }
        } else if (heroArtwork != null) {
            // Nothing to scroll, so the header is shown plainly (no collapse behaviour needed).
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = heroArtwork.placeholderIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    if (heroArtwork.uri != null) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = remember(heroArtwork.uri) {
                                ImageRequest.Builder(context)
                                    .data(heroArtwork.uri)
                                    .size(Size(360, 360))
                                    .build()
                            },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No songs here yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No songs here yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (showToolbar) {
                    AnimatedVisibility(visible = isAtTop) {
                        LibraryToolbar(
                            sortOption = sortOption,
                            onSortOptionSelected = { sortOption = it },
                            onShuffleClick = { onShufflePlay(sortedTracks) },
                            availableSortOptions = availableSortOptions
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = sortedTracks, key = { it.id }) { track ->
                        val isPlayingThis = currentTrack?.id == track.id
                        TrackItem(
                            track = track,
                            isPlayingThisTrack = isPlayingThis,
                            onClick = { onTrackClick(track) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                            showArtwork = showTrackArtwork
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsList(
    albums: List<AlbumSummary>,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty()) {
        EmptyLibrarySection(icon = Icons.Default.Album, message = "No albums found", modifier = modifier)
        return
    }

    val listState = rememberLazyListState()
    val canScroll by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = 120.dp,
                top = 8.dp,
                start = 8.dp,
                end = if (canScroll) PRECISE_SCROLLBAR_WIDTH + PRECISE_SCROLLBAR_EDGE_INSET else 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(albums, key = { it.name }) { album ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onAlbumClick(album.name) }
                        .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Album, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        if (album.artworkUri != null) {
                            val context = LocalContext.current
                            AsyncImage(
                                model = remember(album.artworkUri) {
                                    ImageRequest.Builder(context)
                                        .data(album.artworkUri)
                                        .size(Size(128, 128))
                                        .build()
                                },
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(album.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            text = "${album.artist} • ${album.trackCount} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (canScroll) {
            PreciseScrollbar(
                itemCount = albums.size,
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = PRECISE_SCROLLBAR_EDGE_INSET)
            )
        }
    }
}

@Composable
private fun ArtistsList(
    artists: List<ArtistSummary>,
    onArtistClick: (String) -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) {
        EmptyLibrarySection(icon = Icons.Default.Person, message = "No artists found", modifier = modifier)
        return
    }

    var sortOption by remember { mutableStateOf(LibrarySortOption.NAME) }
    val sortedArtists = remember(artists, sortOption) {
        artists.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    val listState = rememberLazyListState()
    val canScroll by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size }
    }
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = isAtTop) {
            LibraryToolbar(
                sortOption = sortOption,
                onSortOptionSelected = { sortOption = it },
                onShuffleClick = onShuffleClick,
                availableSortOptions = listOf(LibrarySortOption.NAME, LibrarySortOption.ARTIST)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = 120.dp,
                    top = 4.dp,
                    start = 8.dp,
                    end = if (canScroll) PRECISE_SCROLLBAR_WIDTH + PRECISE_SCROLLBAR_EDGE_INSET else 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sortedArtists, key = { it.name }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onArtistClick(artist.name) }
                            .padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            if (artist.artworkUri != null) {
                                val context = LocalContext.current
                                AsyncImage(
                                    model = remember(artist.artworkUri) {
                                        ImageRequest.Builder(context)
                                            .data(artist.artworkUri)
                                            .size(Size(128, 128))
                                            .build()
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Column {
                            Text(artist.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                text = "${artist.trackCount} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (canScroll) {
                PreciseScrollbar(
                    itemCount = sortedArtists.size,
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = PRECISE_SCROLLBAR_EDGE_INSET)
                )
            }
        }
    }
}

@Composable
private fun PlaylistsList(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    recentlyAddedTrack: AudioTrack?,
    mostPlayedTrack: AudioTrack?,
    recentlyPlayedTrack: AudioTrack?,
    onCategoryClick: (SpotlightCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        SpotlightRow(
            recentlyAddedTrack = recentlyAddedTrack,
            mostPlayedTrack = mostPlayedTrack,
            recentlyPlayedTrack = recentlyPlayedTrack,
            onCategoryClick = onCategoryClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        )

        if (playlists.isEmpty()) {
            EmptyLibrarySection(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                message = "No playlists yet.\nTap the + on Now Playing to create one.",
                modifier = Modifier.weight(1f)
            )
            return@Column
        }

        val listState = rememberLazyListState()
        val canScroll by remember {
            derivedStateOf { listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size }
        }
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = 120.dp,
                    top = 8.dp,
                    start = 8.dp,
                    end = if (canScroll) PRECISE_SCROLLBAR_WIDTH + PRECISE_SCROLLBAR_EDGE_INSET else 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPlaylistClick(playlist) }
                            .padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Column {
                            Text(playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                text = "${playlist.trackIds.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (canScroll) {
                PreciseScrollbar(
                    itemCount = playlists.size,
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = PRECISE_SCROLLBAR_EDGE_INSET)
                )
            }
        }
    }
}

/**
 * Three at-a-glance category tiles above the playlist list: Recently Added, Most Played and
 * Recently Played, each showing the matching track's artwork. Tapping a tile opens that
 * category's full list (Recently Added: up to 200 tracks; the other two: up to 125).
 */
@Composable
private fun SpotlightRow(
    recentlyAddedTrack: AudioTrack?,
    mostPlayedTrack: AudioTrack?,
    recentlyPlayedTrack: AudioTrack?,
    onCategoryClick: (SpotlightCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SpotlightTile(
            label = SpotlightCategory.RECENTLY_ADDED.label,
            track = recentlyAddedTrack,
            onClick = { onCategoryClick(SpotlightCategory.RECENTLY_ADDED) },
            modifier = Modifier.weight(1f)
        )
        SpotlightTile(
            label = SpotlightCategory.MOST_PLAYED.label,
            track = mostPlayedTrack,
            onClick = { onCategoryClick(SpotlightCategory.MOST_PLAYED) },
            modifier = Modifier.weight(1f)
        )
        SpotlightTile(
            label = SpotlightCategory.RECENTLY_PLAYED.label,
            track = recentlyPlayedTrack,
            onClick = { onCategoryClick(SpotlightCategory.RECENTLY_PLAYED) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SpotlightTile(
    label: String,
    track: AudioTrack?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp)
            )
            if (track?.artworkUri != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(track.artworkUri) {
                        ImageRequest.Builder(context)
                            .data(track.artworkUri)
                            .size(Size(200, 200))
                            .build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = track?.title ?: "No data yet",
            style = MaterialTheme.typography.labelSmall,
            color = if (track != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyLibrarySection(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * A-Z fast-scroller for a list. Tap or drag along the strip to jump the list to the first item
 * whose label starts with that letter; a bubble previews the letter while touching.
 */
@Composable
private fun AlphabetScrollbar(
    labels: List<String>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    // Default behaviour scrolls the list to the jumped-to index. Album detail overrides this
    // when its list isn't scrollable (see DetailTrackList): scrollToItem there would still nudge
    // the list by whatever sliver of range contentPadding leaves it, and since that nudge is a
    // programmatic jump rather than a drag it doesn't flow through the header's scroll-delta
    // tracking (headerPushConnection) -- the header and list would fall out of sync with no way
    // for the header to know a jump happened. Highlighting instead of scrolling sidesteps the
    // problem entirely rather than trying to patch the tracking for this one case.
    onJump: ((index: Int) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val letters = remember { ('A'..'Z').map { it.toString() } }
    var trackHeightPx by remember { mutableStateOf(0f) }

    // First index in `labels` for each letter; labels that don't start with A-Z group under '#'.
    val letterIndex = remember(labels) {
        val map = HashMap<String, Int>()
        labels.forEachIndexed { index, label ->
            val first = label.firstOrNull()?.uppercaseChar()
            val key = if (first != null && first in 'A'..'Z') first.toString() else "#"
            if (key !in map) map[key] = index
        }
        map
    }

    var activeLetter by remember { mutableStateOf<String?>(null) }

    fun jumpTo(letter: String) {
        activeLetter = letter
        letterIndex[letter]?.let { index ->
            if (onJump != null) {
                onJump(index)
            } else {
                coroutineScope.launch { listState.scrollToItem(index) }
            }
        }
    }

    Box(modifier = modifier) {
        activeLetter?.let { letter ->
            // Vertically align the bubble with this letter's actual row in the strip below.
            val activeIndex = letters.indexOf(letter)
            val bubbleSizePx = with(LocalDensity.current) { 52.dp.toPx() }
            val centerY = if (trackHeightPx > 0f) {
                (activeIndex + 0.5f) / letters.size * trackHeightPx
            } else {
                trackHeightPx / 2f
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 5.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 36.dp)
                    .offset { IntOffset(0, (centerY - bubbleSizePx / 2f).roundToInt()) }
                    .size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = letter,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(ALPHABET_SCROLLBAR_WIDTH)
                .systemGestureExclusion()
                .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
                .pointerInput(letters, letterIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val height = size.height.toFloat()
                        fun letterForY(y: Float): String {
                            val index = (y / height * letters.size).toInt().coerceIn(0, letters.size - 1)
                            return letters[index]
                        }
                        down.consume()
                        jumpTo(letterForY(down.position.y))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            jumpTo(letterForY(change.position.y))
                        }
                        activeLetter = null
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            letters.forEach { letter ->
                val isActive = letter == activeLetter
                // Read via graphicsLayer's draw-phase lambda rather than `by` in the composable
                // body, so animating the active letter's scale doesn't recompose all 26 Texts
                // on every animation frame -- only the draw phase re-runs.
                val scale = animateFloatAsState(targetValue = if (isActive) 2.4f else 1f, label = "letterScale")
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                )
            }
        }
    }
}

/**
 * A precise, position-proportional scrollbar (thumb reflects exact scroll position and viewport
 * fraction). Drag anywhere along the track to jump straight there, unlike the alphabetical index.
 */
@Composable
private fun PreciseScrollbar(
    itemCount: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    // See the identical parameter on AlphabetScrollbar for why album detail overrides this.
    onJump: ((index: Int) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // layoutInfo changes on every frame of a scroll, so reading it directly in the composable
    // body would recompose this scrollbar every frame. derivedStateOf narrows that down to the
    // frames where the values actually change.
    val maxFirstIndex by remember(itemCount) {
        derivedStateOf {
            val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            (itemCount - visibleCount).coerceAtLeast(1)
        }
    }
    val thumbFraction by remember(itemCount) {
        derivedStateOf {
            val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            (visibleCount.toFloat() / itemCount).coerceIn(0.1f, 1f)
        }
    }
    val progress by remember(itemCount) {
        derivedStateOf {
            (listState.firstVisibleItemIndex.toFloat() / maxFirstIndex).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .width(PRECISE_SCROLLBAR_WIDTH)
            .systemGestureExclusion()
            .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
            .pointerInput(itemCount, maxFirstIndex) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    val height = size.height.toFloat()
                    fun scrollToY(y: Float) {
                        val fraction = (y / height).coerceIn(0f, 1f)
                        val targetIndex = (fraction * maxFirstIndex).roundToInt().coerceIn(0, itemCount - 1)
                        if (onJump != null) {
                            onJump(targetIndex)
                        } else {
                            coroutineScope.launch { listState.scrollToItem(targetIndex) }
                        }
                    }
                    down.consume()
                    scrollToY(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        scrollToY(change.position.y)
                    }
                    isDragging = false
                }
            }
    ) {
        // Track line
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        )

        // Draggable thumb
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (progress * trackHeightPx * (1f - thumbFraction)).roundToInt()) }
                .fillMaxHeight(thumbFraction)
                .width(if (isDragging) 8.dp else 5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

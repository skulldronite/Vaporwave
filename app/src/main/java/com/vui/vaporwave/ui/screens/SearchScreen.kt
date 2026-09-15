package com.vui.vaporwave.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vui.vaporwave.model.AudioTrack
import com.vui.vaporwave.ui.components.MiniPlayer
import com.vui.vaporwave.ui.components.TrackItem
import kotlinx.coroutines.delay

/**
 * Full-screen search "card", rendered as a top-level overlay (a sibling of the Scaffold, not
 * nested inside its content slot) so it owns its own status bar and IME insets rather than
 * inheriting Scaffold's innerPadding -- avoiding the double-reservation bugs that came from
 * mixing the two last time. Uses the ambient [MaterialTheme.colorScheme] inherited from the
 * app's own VaporwaveTheme wrapper, so it follows dark mode and the vaporwave/dynamic-color
 * toggle exactly like every other screen instead of a fixed light palette.
 */
@Composable
fun SearchScreen(
    query: String,
    results: List<AudioTrack>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    /** Read lazily so the position tick doesn't recompose the whole search card -- see [MiniPlayer]. */
    progress: () -> Float,
    /**
     * Bumped by the ViewModel every time openSearch() is called. Used to key the placeholder
     * joke pick below -- see the comment there for why relying on this card's own composition
     * lifetime isn't reliable for that.
     */
    openSequence: Int,
    onQueryChange: (String) -> Unit,
    onTrackClick: (AudioTrack) -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    onBack: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onExpandNowPlaying: () -> Unit,
    onNowPlayingDragDelta: (Float) -> Unit,
    onNowPlayingDragStopped: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Opening the card should put the cursor straight in the field -- the user tapped search
    // because they intend to type immediately.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    var sortOption by remember { mutableStateOf(SearchSortOption.TRACK_NAME) }
    var sortDirection by remember { mutableStateOf(SearchSortDirection.ASCENDING) }
    val sortedResults = remember(results, sortOption, sortDirection) {
        sortSearchResults(results, sortOption, sortDirection)
    }
    val closestMatchId = remember(results, query) { findClosestMatch(results, query)?.id }

    // Tracks which rows have already played their entrance animation, for as long as this
    // search card stays open -- LazyColumn only keeps a small buffer of off-screen items
    // composed, so scrolling a row far enough away and back disposes and recreates its
    // remember{} state, which would otherwise replay the animation every time it's scrolled
    // back into view. A plain mutable set (not observable state) is enough here: it's only ever
    // read once, at the moment a row's own remember block runs.
    val animatedTrackIds = remember { mutableSetOf<Long>() }

    // Picked once per time the card opens rather than per keystroke -- a joke that changes out
    // from under you while you're still typing would just be distracting. Keyed on openSequence
    // (bumped by the ViewModel on every openSearch() call) rather than plain remember{} --
    // AnimatedVisibility can keep this card's composition alive across a close immediately
    // followed by a reopen (its exit transition hasn't finished tearing the content down yet),
    // which meant a plain remember{} never actually re-ran and the same line stuck around no
    // matter how many times the card was reopened. Also excludes whatever was shown last time
    // (tracked outside composition so it survives across opens) so consecutive genuine opens
    // don't have a real chance of repeating the same line back to back.
    val placeholderJoke = remember(openSequence) {
        searchPlaceholderJokes.filterNot { it == lastPlaceholderJoke }
            .random()
            .also { lastPlaceholderJoke = it }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        // Only the top corners round off, since this covers the full screen and its bottom
        // edge sits flush with the device's own bottom edge -- rounding there would just cut
        // into content for no visual reason.
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        // Boxed rather than a single Column: the search card fully covers the Scaffold's own
        // mini player underneath it, so without one drawn in here too, playing a track while
        // the search card is open leaves no visible player at all until it's closed.
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // Full-screen overlay, so it never gets Scaffold's own bottom-bar inset handling
                // -- without this, the last search result can end up behind a legacy 3-button
                // nav bar (e.g. the Galaxy S9), which is tall and opaque unlike gesture nav's
                // thin inset.
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { keyboardController?.hide(); onBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(placeholderJoke) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { keyboardController?.hide() }
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear query")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
            }

            when {
                query.isBlank() -> SearchPrompt("Search your library by song, artist, album or format")

                results.isEmpty() -> SearchPrompt("No tracks match \"$query\"")

                else -> {
                    SearchToolbar(
                        sortOption = sortOption,
                        onSortOptionSelected = { sortOption = it },
                        sortDirection = sortDirection,
                        onSortDirectionSelected = { sortDirection = it },
                        onShuffleClick = {
                            keyboardController?.hide()
                            onShufflePlay(sortedResults)
                        }
                    )

                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp, start = 8.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(items = sortedResults, key = { _, track -> track.id }) { index, track ->
                            // Rows genuinely new to the composition (a track that wasn't already
                            // showing, and hasn't already played its entrance elsewhere in this
                            // search session) animate in; rows that were already visible, or are
                            // scrolling back into view after having played once already, appear
                            // immediately at full opacity instead of replaying. Staggered by list
                            // position for a ripple-down-the-list reveal instead of every row
                            // popping in at once -- capped low and quick so scrolling to reveal
                            // further rows doesn't feel like it's lagging behind the scroll.
                            val alreadyAnimated = remember(track.id) { track.id in animatedTrackIds }
                            val appear = remember { Animatable(if (alreadyAnimated) 1f else 0f) }
                            LaunchedEffect(Unit) {
                                if (!alreadyAnimated) {
                                    // Marked immediately, not after the animation completes --
                                    // scrolling this row out of view mid-animation cancels this
                                    // effect before it would otherwise get marked, which would
                                    // make it replay from scratch instead of just appearing.
                                    animatedTrackIds += track.id
                                    delay(minOf(index, 4) * 6L)
                                    appear.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
                                }
                            }
                            TrackItem(
                                track = track,
                                isPlayingThisTrack = currentTrack?.id == track.id,
                                isHighlighted = track.id == closestMatchId,
                                onClick = {
                                    keyboardController?.hide()
                                    onTrackClick(track)
                                },
                                modifier = Modifier
                                    .animateItem()
                                    .graphicsLayer {
                                        alpha = appear.value
                                        val fromScale = 0.92f
                                        scaleX = fromScale + (1f - fromScale) * appear.value
                                        scaleY = fromScale + (1f - fromScale) * appear.value
                                        translationY = (1f - appear.value) * 18.dp.toPx()
                                    }
                            )
                        }
                    }
                }
            }
        }

        if (currentTrack != null) {
            MiniPlayer(
                track = currentTrack,
                isPlaying = isPlaying,
                progress = progress,
                onPlayPauseClick = onPlayPauseClick,
                onSkipNextClick = onSkipNextClick,
                onSkipPreviousClick = onSkipPreviousClick,
                onExpandClick = onExpandNowPlaying,
                onDragDelta = onNowPlayingDragDelta,
                onDragStopped = onNowPlayingDragStopped,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        }
    }
}

// Lives outside composition (rather than in a remember block) so it survives the search card
// being closed and reopened -- see the comment where it's read, above.
private var lastPlaceholderJoke: String? = null

// Every entry must stay at or under 23 characters (including spaces) so it never wraps or
// truncates in the search field's placeholder slot. Kept impersonal -- about the music/library,
// not jokes directed at the person searching.
private val searchPlaceholderJokes = listOf(
    "Search here!",
    "Whatever you got!",
    "Type something...",
    "Hunting for a track?",
    "Got a tune in mind?",
    "Your library awaits...",
    "Discover something new",
    "Find that earworm",
    "Ready when you are...",
    "Browse the library"
)

/**
 * The single best match for [query] among [tracks], so it can be highlighted -- an exact title
 * match beats a title that merely starts with it, which beats one that just contains it, which
 * beats a match found only in artist/album/format. Ties keep whichever came first.
 */
private fun findClosestMatch(tracks: List<AudioTrack>, query: String): AudioTrack? {
    if (query.isBlank()) return null
    fun score(track: AudioTrack): Int {
        val title = track.title
        return when {
            title.equals(query, ignoreCase = true) -> 5
            title.startsWith(query, ignoreCase = true) -> 4
            title.contains(query, ignoreCase = true) -> 3
            track.artist.contains(query, ignoreCase = true) || track.album.contains(query, ignoreCase = true) -> 2
            else -> 1
        }
    }
    return tracks.maxByOrNull { score(it) }
}

enum class SearchSortOption(val label: String, val icon: ImageVector) {
    TRACK_NAME("Track Name", Icons.Default.MusicNote),
    ARTIST("Artist", Icons.Default.Person),
    ALBUM("Album", Icons.Default.Album),
    FORMAT("Format", Icons.Default.GraphicEq)
}

enum class SearchSortDirection(val label: String, val icon: ImageVector) {
    ASCENDING("Ascending", Icons.Default.ArrowUpward),
    DESCENDING("Descending", Icons.Default.ArrowDownward)
}

private fun sortSearchResults(
    tracks: List<AudioTrack>,
    sortOption: SearchSortOption,
    sortDirection: SearchSortDirection
): List<AudioTrack> {
    val sorted = when (sortOption) {
        SearchSortOption.TRACK_NAME -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        SearchSortOption.ARTIST -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
        SearchSortOption.ALBUM -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album })
        SearchSortOption.FORMAT -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.formatBadge })
    }
    return if (sortDirection == SearchSortDirection.DESCENDING) sorted.asReversed() else sorted
}

/** Sort field + Ascending/Descending filter + shuffle dice, shown above search results. */
@Composable
private fun SearchToolbar(
    sortOption: SearchSortOption,
    onSortOptionSelected: (SearchSortOption) -> Unit,
    sortDirection: SearchSortDirection,
    onSortDirectionSelected: (SearchSortDirection) -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    var isDirectionMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    SearchSortOption.entries.forEach { option ->
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

            Box {
                TextButton(onClick = { isDirectionMenuExpanded = true }) {
                    Icon(
                        imageVector = sortDirection.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(sortDirection.label, style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(
                    expanded = isDirectionMenuExpanded,
                    onDismissRequest = { isDirectionMenuExpanded = false }
                ) {
                    SearchSortDirection.entries.forEach { direction ->
                        DropdownMenuItem(
                            text = { Text(direction.label) },
                            leadingIcon = { Icon(direction.icon, contentDescription = null) },
                            trailingIcon = {
                                if (direction == sortDirection) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                onSortDirectionSelected(direction)
                                isDirectionMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        ShuffleDiceButton(onShuffleClick = onShuffleClick)
    }
}

@Composable
private fun SearchPrompt(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(48.dp))
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

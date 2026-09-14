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
    onQueryChange: (String) -> Unit,
    onTrackClick: (AudioTrack) -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    onBack: () -> Unit,
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

    // Picked once per time the card opens rather than per keystroke -- a joke that changes out
    // from under you while you're still typing would just be distracting.
    val placeholderJoke = remember { searchPlaceholderJokes.random() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        // Only the top corners round off, since this covers the full screen and its bottom
        // edge sits flush with the device's own bottom edge -- rounding there would just cut
        // into content for no visual reason.
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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
                            // A fresh Animatable per key -- only rows genuinely new to the
                            // composition (a track that wasn't already showing before this
                            // keystroke) animate in; rows that were already visible and just
                            // shift position aren't replayed. Staggered by list position for a
                            // ripple-down-the-list reveal instead of every row popping in at once.
                            val appear = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                delay(minOf(index, 10) * 25L)
                                appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
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
    }
}

private val searchPlaceholderJokes = listOf(
    "Search here!",
    "Whatever you got!",
    "Type something, anything...",
    "What are we hunting for today?",
    "Got a song stuck in your head?",
    "Your library awaits...",
    "Go on, I don't judge your music taste",
    "Looking for something (besides your keys)?",
    "Find that one song you can't stop humming",
    "Ready when you are..."
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

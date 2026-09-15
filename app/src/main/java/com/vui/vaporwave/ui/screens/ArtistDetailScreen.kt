package com.vui.vaporwave.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import android.net.Uri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.vui.vaporwave.model.AudioTrack
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Which of the artist screen's two layouts is showing. Always starts on Tracks when reopened. */
enum class ArtistViewMode { TRACKS, ALBUMS }

/** Sort order for the Albums grid -- distinct from LibrarySortOption, since these sort whole
 *  albums (by their combined duration or release year) rather than individual tracks. */
enum class ArtistAlbumSortOption(val label: String) {
    NAME("Name"),
    LONGEST("Longest First"),
    SHORTEST("Shortest First"),
    RELEASE_ORDER("Release Order")
}

/** One of an artist's albums, with everything the Tracks/Albums views need already resolved. */
data class ArtistAlbumGroup(
    val name: String,
    val year: Int,
    val artworkUri: Uri?,
    val tracks: List<AudioTrack>
)

/** Groups an artist's tracks by album, sorted within each album by disc then track number. */
fun groupArtistTracksByAlbum(tracks: List<AudioTrack>): List<ArtistAlbumGroup> =
    tracks.groupBy { it.album }
        .map { (albumName, albumTracks) ->
            ArtistAlbumGroup(
                name = albumName,
                year = albumTracks.firstOrNull { it.year > 0 }?.year ?: 0,
                artworkUri = albumTracks.firstOrNull { it.artworkUri != null }?.artworkUri,
                tracks = albumTracks.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
            )
        }

/**
 * Artist detail's own screen -- structurally unlike album detail (which this used to share via
 * DetailTrackList), so it's a dedicated composable rather than another mode bolted onto that one.
 *
 * Tracks mode groups every track under its album (alphabetically), showing each album's art/name/
 * year once above its tracks. Albums mode instead shows a 3-wide grid of this artist's albums,
 * each one opening the existing album detail screen (LibraryDetail.Album) rather than a separate
 * copy of it, so backing out of an album returns here via the same detail stack already used
 * elsewhere in the app.
 */
@Composable
fun ArtistDetailScreen(
    title: String,
    subtitle: String,
    heroArtworkUri: Uri?,
    albumGroups: List<ArtistAlbumGroup>,
    currentTrack: AudioTrack?,
    onBack: () -> Unit,
    onTrackClick: (AudioTrack) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onOpenTrackDetails: (AudioTrack) -> Unit,
    // The overlay this screen renders in (see MainActivity) draws its own MiniPlayer as a sibling
    // rather than through Scaffold's innerPadding, so this is the mini player's real measured
    // height rather than a guess -- letting the list scroll fully clear of it regardless of the
    // device's navigation bar style/inset, which a fixed dp constant can't account for.
    bottomContentPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    // rememberSaveable, not remember: opening an album from the Albums grid pushes it on top of
    // this screen in MainActivity's detail stack, which disposes this composable outright rather
    // than just hiding it -- plain remember state would reset back to Tracks/Name by the time you
    // navigated back. MainActivity wraps each stack level in a SaveableStateProvider keyed by that
    // level's identity, which is what actually lets this survive that dispose/recreate cycle.
    var viewMode by rememberSaveable { mutableStateOf(ArtistViewMode.TRACKS) }
    var albumSort by rememberSaveable { mutableStateOf(ArtistAlbumSortOption.NAME) }

    // Tracks and Albums are two branches sharing a single LazyColumn item (see the AnimatedContent
    // below) rather than separate lists, so they also share one LazyListState. AnimatedContent has
    // no SizeTransform here, so that item's measured height snaps straight to the incoming
    // content's height the instant viewMode changes -- if the list was scrolled deep into a long
    // Tracks section, that offset is now invalid against Albums' much shorter content, and Compose
    // has to clamp it back into bounds on the next layout pass. That reactive clamp is what showed
    // up as a blank frame before Albums content "snapped" into view. Resetting scroll to the top
    // ourselves the moment the mode changes means the offset is never invalid in the first place.
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Tracks *why* viewMode is whatever it currently is, not *when* it became that -- deliberately
    // plain remember (always starts false on a fresh composition), not rememberSaveable: only the
    // pill/swipe's own handler below ever sets this true, so a restored viewMode (e.g. coming back
    // to Albums after closing an album opened from this screen) never flips it, and the pill/
    // content-switch animations both stay off until an actual tap or swipe happens. An earlier
    // attempt guessed restoration finished "one frame after mount" and gated on that instead --
    // wrong guess, since the animation still played, so this tracks the real cause directly rather
    // than trying to time it.
    var viewModeChangedByUser by remember { mutableStateOf(false) }
    fun setViewMode(mode: ArtistViewMode) {
        if (mode != viewMode) {
            // Instant jump (not an animated scroll) so the position is already valid for the
            // incoming content by the time AnimatedContent swaps it in, rather than animating from
            // a soon-to-be-invalid offset.
            coroutineScope.launch { listState.scrollToItem(0) }
        }
        viewMode = mode
        viewModeChangedByUser = true
    }

    val allTracks = remember(albumGroups) { albumGroups.flatMap { it.tracks } }
    // Computed per album group, not on the flattened list -- see [stableTrackNumbers]'s doc on
    // why numbering has to stay scoped to one album's own discs at a time.
    val trackNumbers = remember(albumGroups) {
        buildMap { albumGroups.forEach { group -> putAll(stableTrackNumbers(group.tracks)) } }
    }
    val tracksModeGroups = remember(albumGroups) {
        albumGroups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    val albumsModeGroups = remember(albumGroups, albumSort) {
        when (albumSort) {
            ArtistAlbumSortOption.NAME -> albumGroups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            ArtistAlbumSortOption.LONGEST -> albumGroups.sortedByDescending { g -> g.tracks.sumOf { it.durationMs } }
            ArtistAlbumSortOption.SHORTEST -> albumGroups.sortedBy { g -> g.tracks.sumOf { it.durationMs } }
            // Untagged (year == 0) albums sink to the bottom regardless of direction -- there's no
            // sane place to put "unknown" in a chronological order.
            ArtistAlbumSortOption.RELEASE_ORDER -> albumGroups.sortedWith(
                compareByDescending<ArtistAlbumGroup> { it.year > 0 }.thenByDescending { it.year }
            )
        }
    }

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val heroCollapseFraction by remember {
        derivedStateOf {
            if (headerHeightPx <= 0) {
                0f
            } else if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / headerHeightPx.toFloat()).coerceIn(0f, 1f)
            }
        }
    }
    val context = LocalContext.current

    // A swipe anywhere on this screen -- header included, not just over the track/album rows --
    // triggers the same tab switch the pill does. It's a threshold gesture (drag far enough, then
    // it snaps to the fixed crossfade below), not a finger-tracking pager: a real pager needs to
    // own its own horizontal drag axis independently of this list's vertical one, which is more
    // than what's being asked for here. Hoisted to this outer scope (rather than declared inside
    // the LazyColumn's own item{} block) so the gesture can be attached to the Box that wraps the
    // whole screen body below, not just the list content beneath the header.
    var dragAccumulatorPx by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // Attached here rather than on the AnimatedContent below (as it used to be) so the
                // whole screen -- hero artwork, title, toolbar, and the list -- is a valid swipe
                // zone, not just the track/album rows themselves. Vertical scrolling is unaffected:
                // detectHorizontalDragGestures only claims a touch once it crosses a *horizontal*
                // touch-slop threshold, so a vertical drag is left unconsumed for the LazyColumn's
                // own scroll handling underneath to pick up, exactly as it already did when this
                // was scoped to a single item.
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulatorPx = 0f },
                        onDragEnd = {
                            if (dragAccumulatorPx <= -swipeThresholdPx) {
                                setViewMode(ArtistViewMode.ALBUMS)
                            } else if (dragAccumulatorPx >= swipeThresholdPx) {
                                setViewMode(ArtistViewMode.TRACKS)
                            }
                            dragAccumulatorPx = 0f
                        },
                        onDragCancel = { dragAccumulatorPx = 0f }
                    ) { change, dragAmount ->
                        dragAccumulatorPx += dragAmount
                        change.consume()
                    }
                }
        ) {
            LazyColumn(
                state = listState,
                // A real (layout-shrinking) padding, not just contentPadding -- contentPadding
                // only adds blank scroll space before the first/after the last item, it doesn't
                // shrink this LazyColumn's own clip bounds. Since the mini player floats as a
                // rounded pill with transparent margins around it (see MiniPlayer.kt), rows laid
                // out at the very bottom of a fillMaxSize list showed through those margins at
                // any scroll position, not just once scrolled to the true end -- shrinking the
                // list's own box means rows can never be placed there at all.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomContentPadding),
                contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { headerHeightPx = it.height }
                            .graphicsLayer { alpha = 1f - heroCollapseFraction },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(52.dp))
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(56.dp)
                            )
                            if (heroArtworkUri != null) {
                                AsyncImage(
                                    model = remember(heroArtworkUri) {
                                        ImageRequest.Builder(context)
                                            .data(heroArtworkUri)
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ArtistModeToolbar(
                            viewMode = viewMode,
                            onViewModeChange = { setViewMode(it) },
                            albumSort = albumSort,
                            onAlbumSortChange = { albumSort = it },
                            onShuffleClick = { onShufflePlay(allTracks) },
                            animatePill = viewModeChangedByUser,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // A single lazy item rather than the individual items/groups this used to be laid
                // out as: AnimatedContent needs one bounded region it owns to crossfade between,
                // it can't animate a transition across a variable range of a LazyColumn's own
                // items. The tradeoff is that everything below the header is now composed eagerly
                // instead of only the rows currently on screen -- acceptable for a per-artist
                // track/album count, but this is why it isn't done for a whole-library list.
                item {
                    AnimatedContent(
                        targetState = viewMode,
                        // `using null`, same reasoning as the library-detail stack transition in
                        // MainActivity: Tracks and Albums content are rarely the same height, and
                        // AnimatedContent's default SizeTransform would clip/zoom across that
                        // difference instead of just letting the crossfade play out in place.
                        transitionSpec = {
                            if (!viewModeChangedByUser) {
                                // Skips animating a restored value -- e.g. leaving an album that
                                // was opened from the Albums tab recomposes this screen fresh, and
                                // without this guard it visibly played the Tracks-to-Albums wipe
                                // below as if the user had just switched tabs, instead of it
                                // simply already having been on Albums.
                                fadeIn(snap()) togetherWith fadeOut(snap())
                            } else {
                                // A directional wipe rather than a plain crossfade -- new content
                                // slides in from the side matching whichever tab it's coming from,
                                // old content slides out the opposite way, matching the swipe
                                // gesture's own left/right sense.
                                val direction = if (targetState == ArtistViewMode.ALBUMS) 1 else -1
                                (slideInHorizontally(initialOffsetX = { width -> direction * width }) + fadeIn()) togetherWith
                                    (slideOutHorizontally(targetOffsetX = { width -> -direction * width }) + fadeOut()) using null
                            }
                        },
                        label = "artistViewMode",
                        modifier = Modifier.fillMaxWidth()
                    ) { mode ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            when (mode) {
                                ArtistViewMode.TRACKS -> {
                                    tracksModeGroups.forEachIndexed { index, group ->
                                        if (index > 0) {
                                            Spacer(modifier = Modifier.height(20.dp))
                                        }
                                        AlbumGroupHeader(group, modifier = Modifier.padding(horizontal = 8.dp))
                                        group.tracks.forEach { track ->
                                            DiscTrackRow(
                                                track = track,
                                                isPlayingThisTrack = currentTrack?.id == track.id,
                                                onClick = { onTrackClick(track) },
                                                onAddToPlaylist = { onAddToPlaylist(track) },
                                                onShare = { shareTrack(context, track) },
                                                onTrackDetails = { onOpenTrackDetails(track) },
                                                leadingNumber = trackNumbers[track.id] ?: track.trackNumber,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                        }
                                    }
                                }
                                ArtistViewMode.ALBUMS -> {
                                    albumsModeGroups.chunked(3).forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            row.forEach { group ->
                                                AlbumSquare(
                                                    group = group,
                                                    onClick = { onOpenAlbum(group.name) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            repeat(3 - row.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 4.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    }
}

/**
 * The Tracks/Albums switch plus (Albums mode only) the album sort menu and the shared shuffle
 * dice. The sort button fades and scales in when Albums is selected, and back out when Tracks is
 * reselected, rather than just appearing/disappearing.
 */
@Composable
private fun ArtistModeToolbar(
    viewMode: ArtistViewMode,
    onViewModeChange: (ArtistViewMode) -> Unit,
    albumSort: ArtistAlbumSortOption,
    onAlbumSortChange: (ArtistAlbumSortOption) -> Unit,
    onShuffleClick: () -> Unit,
    animatePill: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewModeToggle(viewMode = viewMode, onChange = onViewModeChange, animate = animatePill)

        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = viewMode == ArtistViewMode.ALBUMS,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                var isSortMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { isSortMenuExpanded = true }) {
                        Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort albums")
                    }
                    DropdownMenu(
                        expanded = isSortMenuExpanded,
                        onDismissRequest = { isSortMenuExpanded = false }
                    ) {
                        ArtistAlbumSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                trailingIcon = {
                                    if (option == albumSort) Icon(Icons.Default.Check, contentDescription = null)
                                },
                                onClick = {
                                    onAlbumSortChange(option)
                                    isSortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            ShuffleDiceButton(onShuffleClick = onShuffleClick)
        }
    }
}

@Composable
private fun ViewModeToggle(viewMode: ArtistViewMode, onChange: (ArtistViewMode) -> Unit, animate: Boolean) {
    // Each label's own measured width (including its padding) -- NOT an even weighted split of
    // the pill's total width. "Tracks" and "Albums" aren't the same length, and forcing an even
    // split (via Modifier.weight) was what blew the whole pill up to fill all the space its parent
    // Row offered it instead of just wrapping its own two short labels.
    var tracksWidthPx by remember { mutableIntStateOf(0) }
    var albumsWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val targetOffsetPx = if (viewMode == ArtistViewMode.TRACKS) 0f else tracksWidthPx.toFloat()
    val targetWidthPx = if (viewMode == ArtistViewMode.TRACKS) tracksWidthPx.toFloat() else albumsWidthPx.toFloat()
    // `animate == false` snaps instantly instead of sliding -- used while the caller's viewMode
    // hasn't settled yet (e.g. right after this screen is restored coming back from an album), so
    // that settling doesn't itself look like a user-triggered tab switch.
    val offsetSpec = if (animate) spring<Float>() else snap()
    val widthSpec = if (animate) spring<Float>() else snap()
    val animatedOffsetPx by animateFloatAsState(targetValue = targetOffsetPx, animationSpec = offsetSpec, label = "pillHighlightOffset")
    val animatedWidthPx by animateFloatAsState(targetValue = targetWidthPx, animationSpec = widthSpec, label = "pillHighlightWidth")

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(2.dp)
            // Resolves a concrete height from the Row's own content first, so the highlight below
            // (which needs an explicit height to fillMaxHeight against) isn't asking this Box to
            // size itself from a child that's simultaneously asking to fill that same size.
            .height(IntrinsicSize.Min)
    ) {
        if (tracksWidthPx > 0 && albumsWidthPx > 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                    .width(with(density) { animatedWidthPx.toDp() })
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Row {
            listOf(ArtistViewMode.TRACKS to "Tracks", ArtistViewMode.ALBUMS to "Albums").forEach { (mode, label) ->
                val selected = mode == viewMode
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .onSizeChanged { size ->
                            if (mode == ArtistViewMode.TRACKS) tracksWidthPx = size.width else albumsWidthPx = size.width
                        }
                        .clickable { onChange(mode) }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumGroupHeader(group: ArtistAlbumGroup, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(30.dp)
            )
            if (group.artworkUri != null) {
                AsyncImage(
                    model = remember(group.artworkUri) {
                        ImageRequest.Builder(context).data(group.artworkUri).size(Size(200, 200)).build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (group.year > 0) {
                Text(
                    text = group.year.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "${group.tracks.size} " + if (group.tracks.size == 1) "track" else "tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlbumSquare(group: ArtistAlbumGroup, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp)
            )
            if (group.artworkUri != null) {
                AsyncImage(
                    model = remember(group.artworkUri) {
                        ImageRequest.Builder(context).data(group.artworkUri).size(Size(300, 300)).build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = group.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

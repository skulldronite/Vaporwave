package com.vui.vaporwave.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.vui.vaporwave.model.AudioTrack

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
    modifier: Modifier = Modifier
) {
    // rememberSaveable, not remember: opening an album from the Albums grid pushes it on top of
    // this screen in MainActivity's detail stack, which disposes this composable outright rather
    // than just hiding it -- plain remember state would reset back to Tracks/Name by the time you
    // navigated back. MainActivity wraps each stack level in a SaveableStateProvider keyed by that
    // level's identity, which is what actually lets this survive that dispose/recreate cycle.
    var viewMode by rememberSaveable { mutableStateOf(ArtistViewMode.TRACKS) }
    var albumSort by rememberSaveable { mutableStateOf(ArtistAlbumSortOption.NAME) }

    val allTracks = remember(albumGroups) { albumGroups.flatMap { it.tracks } }
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

    val listState = rememberLazyListState()
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

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp),
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
                            onViewModeChange = { viewMode = it },
                            albumSort = albumSort,
                            onAlbumSortChange = { albumSort = it },
                            onShuffleClick = { onShufflePlay(allTracks) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                when (viewMode) {
                    ArtistViewMode.TRACKS -> {
                        tracksModeGroups.forEachIndexed { index, group ->
                            if (index > 0) {
                                item(key = "gap_${group.name}") {
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }
                            item(key = "header_${group.name}") {
                                AlbumGroupHeader(group, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                            items(items = group.tracks, key = { it.id }) { track ->
                                DiscTrackRow(
                                    track = track,
                                    isPlayingThisTrack = currentTrack?.id == track.id,
                                    onClick = { onTrackClick(track) },
                                    onAddToPlaylist = { onAddToPlaylist(track) },
                                    onShare = { shareTrack(context, track) },
                                    onTrackDetails = { onOpenTrackDetails(track) },
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                    ArtistViewMode.ALBUMS -> {
                        items(albumsModeGroups.chunked(3), key = { row -> row.joinToString { it.name } }) { row ->
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewModeToggle(viewMode = viewMode, onChange = onViewModeChange)

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
private fun ViewModeToggle(viewMode: ArtistViewMode, onChange: (ArtistViewMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(2.dp)
    ) {
        listOf(ArtistViewMode.TRACKS to "Tracks", ArtistViewMode.ALBUMS to "Albums").forEach { (mode, label) ->
            val selected = mode == viewMode
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onChange(mode) }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
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

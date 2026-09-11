package com.vui.vaporwave

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale
import com.vui.vaporwave.model.AudioTrack
import com.vui.vaporwave.theme.VaporwaveTheme
import com.vui.vaporwave.ui.AppDestination
import com.vui.vaporwave.ui.LibraryDetail
import com.vui.vaporwave.ui.MusicViewModel
import com.vui.vaporwave.ui.SpotlightCategory
import com.vui.vaporwave.ui.components.MiniPlayer
import com.vui.vaporwave.ui.components.NowPlayingSheet
import com.vui.vaporwave.ui.components.PlaylistPickerDialog
import com.vui.vaporwave.ui.components.VaporwaveTopBar
import com.vui.vaporwave.ui.screens.DetailTrackList
import com.vui.vaporwave.ui.screens.EffectsScreen
import com.vui.vaporwave.ui.screens.FilesScreen
import com.vui.vaporwave.ui.screens.HeroArtwork
import com.vui.vaporwave.ui.screens.LibraryScreen
import com.vui.vaporwave.ui.screens.SearchScreen
import com.vui.vaporwave.ui.screens.SettingsScreen
import com.vui.vaporwave.ui.screens.SplashScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The manifest already declares adjustNothing, but some OEM skins (observed on a Vivo
        // running Android 16) ignore that declaration and pan/resize the window on their own when
        // the IME opens -- which then stacks with our own imePadding() reservations in the search
        // card, pushing content up twice. Setting it again here forces the real window flag.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // Handle external audio file if opened via Intent VIEW
        handleIncomingIntent(intent)

        setContent {
            val useVaporwaveTheme by viewModel.useVaporwaveTheme.collectAsStateWithLifecycle()
            val useDarkTheme by viewModel.useDarkTheme.collectAsStateWithLifecycle()

            VaporwaveTheme(
                darkTheme = useDarkTheme,
                useDynamicColor = !useVaporwaveTheme
            ) {
                // Fills the whole window with the theme's background first, so both the
                // reachability pull's reveal gap and any pre-composition frame show the
                // correct color instead of the raw (white) window background.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    // Shown once per process (not on rotation -- MainActivity's configChanges
                    // already keeps onCreate from re-running for that -- and not when just
                    // resuming from recents, since onCreate isn't called then either).
                    var showSplash by remember { mutableStateOf(true) }

                    Crossfade(
                        targetState = showSplash,
                        // The default Crossfade spec is an untuned 300ms tween, which reads more
                        // like an abrupt cut than a deliberate fade -- a bit longer with an
                        // eased curve is what makes it feel like an intentional transition.
                        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                        label = "splash"
                    ) { splashVisible ->
                        if (splashVisible) {
                            SplashScreen(
                                backgroundColor = MaterialTheme.colorScheme.background,
                                onFinished = { showSplash = false }
                            )
                        } else {
                            // Disables the stretch/"jelly" overscroll effect for every scrollable
                            // in the app (all the LazyColumns, the pager, etc.) -- applied once
                            // here rather than per-list since every scrollable reads this same
                            // CompositionLocal.
                            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                                MainAppContent(
                                    viewModel = viewModel,
                                    onPickAudioFile = { openDocumentPicker() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                viewModel.openAndPlayUri(uri)
            }
        }
    }

    private var openDocumentCallback: (() -> Unit)? = null
    private fun openDocumentPicker() {
        openDocumentCallback?.invoke()
    }

    @Composable
    private fun MainAppContent(
        viewModel: MusicViewModel,
        onPickAudioFile: () -> Unit
    ) {
        // Permissions for Android 10 (API 29) to Android 16 (API 36)
        val permissionsLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.values.any { it }
            if (granted) {
                viewModel.loadTracks()
            }
        }

        // Storage Access Framework Picker (Zero permission required)
        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let { viewModel.openAndPlayUri(it) }
        }

        LaunchedEffect(Unit) {
            openDocumentCallback = {
                filePickerLauncher.launch(arrayOf("audio/*"))
            }

            // Request appropriate permissions based on Android API level
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionsLauncher.launch(perms.toTypedArray())
        }

        val destination by viewModel.currentDestination.collectAsStateWithLifecycle()
        val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
        val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
        // Deliberately NOT read with `by` here. The position ticks four times a second, and
        // reading it in this function's body would invalidate the whole screen at that rate.
        // Held as a State and read inside lambdas instead, so only the composables that actually
        // draw the position (the mini player's bar, the Now Playing slider) invalidate.
        val playbackPositionState = viewModel.playbackPosition.collectAsStateWithLifecycle()
        val duration by viewModel.duration.collectAsStateWithLifecycle()
        val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
        val playbackPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()
        val isSlowedAndReverb by viewModel.isSlowedAndReverb.collectAsStateWithLifecycle()
        val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
        val isShuffle by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
        val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()
        val isSearchOpen by viewModel.isSearchOpen.collectAsStateWithLifecycle()
        val libraryDetailStack by viewModel.libraryDetailStack.collectAsStateWithLifecycle()
        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
        val tracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
        val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
        val useVaporwaveTheme by viewModel.useVaporwaveTheme.collectAsStateWithLifecycle()
        val useDarkTheme by viewModel.useDarkTheme.collectAsStateWithLifecycle()
        val favouriteTrackIds by viewModel.favouriteTrackIds.collectAsStateWithLifecycle()
        val favouriteTracks by viewModel.favouriteTracks.collectAsStateWithLifecycle()
        val albums by viewModel.albums.collectAsStateWithLifecycle()
        val artists by viewModel.artists.collectAsStateWithLifecycle()
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        val playlistPickerTarget by viewModel.playlistPickerTarget.collectAsStateWithLifecycle()
        val currentLibraryTab by viewModel.currentLibraryTab.collectAsStateWithLifecycle()
        val showSwipeHint by viewModel.showSwipeHint.collectAsStateWithLifecycle()
        val recentlyAddedTracks by viewModel.recentlyAddedTracks.collectAsStateWithLifecycle()
        val mostPlayedTracks by viewModel.mostPlayedTracks.collectAsStateWithLifecycle()
        val recentlyPlayedTracks by viewModel.recentlyPlayedTracks.collectAsStateWithLifecycle()

        val progressProvider: () -> Float = remember(duration) {
            {
                if (duration > 0) playbackPositionState.value.toFloat() / duration.toFloat() else 0f
            }
        }

        val topBarTitle = when (destination) {
            AppDestination.LIBRARY -> currentLibraryTab.label
            AppDestination.FILES -> "Files"
            AppDestination.EFFECTS -> "FX Studio"
            AppDestination.SETTINGS -> "Settings"
        }

        // One-handed "reachability" gesture: pulling down again once a list is already at the
        // top pushes the top bar and content down (and enlarges the title) so they're easier to
        // reach one-handed, then springs back once released. The mini player stays docked at the
        // bottom rather than moving with the rest -- it should always stay reachable/visible.
        // Held (not read with `by`) at this scope on purpose: onPreScroll/onPostScroll write to
        // this every touch-move frame during the pull, and the settle spring-back writes it every
        // animation frame on release. Reading .floatValue here in MainAppContent's body would
        // make *this* the recomposition scope -- forcing the whole function (all ~25 collected
        // StateFlows, the Scaffold, the entire LibraryScreen tree) to be walked on every one of
        // those frames. Reading it is deferred to inside the topBar slot lambda below instead,
        // which is its own recomposition scope, so a drag/settle frame only ever re-walks the
        // spacer + top bar.
        val pullProgressState = remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current
        val maxPullPx = with(density) { 96.dp.toPx() }

        // How far the Now Playing sheet is open: 0 = collapsed to the mini player, 1 = full
        // screen. Kept as a progress fraction rather than a pixel offset so it stays valid
        // before the container has been measured. Dragging the mini player drives this directly,
        // which is what lets the sheet sit at any in-between position under the finger.
        val sheetProgress = remember { Animatable(0f) }
        var containerHeightPx by remember { mutableFloatStateOf(0f) }
        val scope = rememberCoroutineScope()

        // Tapping the mini player, the collapse button, or system back all go through the
        // ViewModel flag; this drives the animation to match whenever it changes.
        LaunchedEffect(isNowPlayingExpanded) {
            val target = if (isNowPlayingExpanded) 1f else 0f
            if (sheetProgress.value != target) {
                sheetProgress.animateTo(target, tween(durationMillis = 300))
            }
        }

        // Settles to whichever end is closer, unless the fling was fast enough to decide it.
        fun settleSheet(velocityPxPerSec: Float) {
            scope.launch {
                val flingUp = velocityPxPerSec < -800f
                val flingDown = velocityPxPerSec > 800f
                val target = when {
                    flingUp -> 1f
                    flingDown -> 0f
                    else -> if (sheetProgress.value > 0.4f) 1f else 0f
                }
                sheetProgress.animateTo(target, tween(durationMillis = 250))
                viewModel.setNowPlayingExpanded(target == 1f)
            }
        }

        fun dragSheetBy(deltaPx: Float) {
            if (containerHeightPx <= 0f) return
            scope.launch {
                // Drag up is negative, and up means "more open".
                val next = (sheetProgress.value - deltaPx / containerHeightPx).coerceIn(0f, 1f)
                sheetProgress.snapTo(next)
            }
        }

        BackHandler(enabled = isNowPlayingExpanded) {
            viewModel.setNowPlayingExpanded(false)
        }

        // Search closes before Now Playing does, so back never has to be pressed twice to
        // dismiss whichever is on top.
        BackHandler(enabled = isSearchOpen && !isNowPlayingExpanded) {
            viewModel.closeSearch()
        }

        // Same priority chain for a library detail overlay (album/artist/playlist/spotlight):
        // it closes before Now Playing or search, since it can only ever be underneath them.
        // closeLibraryDetail() pops just the top of the stack, so back steps out one level at a
        // time -- e.g. from an album opened out of an artist's Albums tab, back first returns to
        // that artist (at the same tab) before a second press closes the overlay outright.
        BackHandler(enabled = libraryDetailStack.isNotEmpty() && !isSearchOpen && !isNowPlayingExpanded) {
            viewModel.closeLibraryDetail()
        }

        // Lets the sheet be dragged down from anywhere in its body, not just the header, while
        // still leaving its content scrollable: the drag only starts moving the sheet once the
        // content itself has nothing left to consume (i.e. it's scrolled to the top).
        val sheetNestedScroll = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    // Dragging back up while partly collapsed re-opens the sheet before the
                    // content is allowed to scroll.
                    if (available.y < 0f && sheetProgress.value < 1f) {
                        dragSheetBy(available.y)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    // Downward drag the content couldn't use means it's at the top -- take it
                    // and collapse instead of letting the scroll overscroll in place.
                    if (available.y > 0f) {
                        dragSheetBy(available.y)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (sheetProgress.value < 1f) {
                        // Passed through with its sign intact: settleSheet uses the same
                        // convention as a drag gesture's velocity, where negative is upward.
                        // Negating it here made a fast downward flick read as a fling *up* and
                        // snap the sheet back open instead of dismissing it.
                        settleSheet(available.y)
                    }
                    return Velocity.Zero
                }
            }
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height.toFloat() }
        ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                // Read here, not in MainAppContent's body -- see the comment on pullProgressState.
                val pullProgress = pullProgressState.floatValue
                val pullDp = with(density) { (pullProgress * maxPullPx).toDp() }
                // A real (layout-affecting) spacer above the bar rather than a draw-time offset,
                // painted the same color as the bar itself -- so growing it as the pull
                // increases pushes the bar down with no mismatched-color gap revealed above it,
                // and Scaffold's own content padding grows to match automatically.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pullDp)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    )
                    VaporwaveTopBar(
                        title = topBarTitle,
                        showSwipeHint = destination == AppDestination.LIBRARY && showSwipeHint,
                        currentLibraryTab = currentLibraryTab,
                        destination = destination,
                        onSearchClick = { viewModel.openSearch() },
                        onDestinationSelected = { viewModel.setDestination(it) },
                        pullProgress = pullProgress
                    )
                }
            },
            bottomBar = {
                // Mini Player (Docked to the bottom of the screen) -- deliberately not offset by
                // the reachability pull, so it never disappears off-screen while pulled down.
                AnimatedVisibility(
                    visible = currentTrack != null,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    MiniPlayer(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        progress = progressProvider,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onSkipNextClick = { viewModel.skipToNext() },
                        onExpandClick = { viewModel.setNowPlayingExpanded(true) },
                        onDragDelta = { delta -> dragSheetBy(delta) },
                        onDragStopped = { velocity -> settleSheet(velocity) }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Scaffold already shrinks this content slot's height to account for the
                    // top bar's growth (see the pull spacer above), which is also what makes
                    // anything anchored to fillMaxHeight (like the alphabet scrollbar) compress
                    // to fit -- an extra bottom padding here would double that shrink.
                    .padding(innerPadding)
            ) {
                when (destination) {
                    AppDestination.LIBRARY -> {
                        LibraryScreen(
                            tracks = tracks,
                            allTracks = allTracks,
                            currentTrack = currentTrack,
                            searchQuery = searchQuery,
                            favouriteTracks = favouriteTracks,
                            albums = albums,
                            artists = artists,
                            playlists = playlists,
                            recentlyAddedTracks = recentlyAddedTracks,
                            mostPlayedTracks = mostPlayedTracks,
                            recentlyPlayedTracks = recentlyPlayedTracks,
                            currentTab = currentLibraryTab,
                            onTabChanged = { viewModel.setCurrentLibraryTab(it) },
                            onSwipeDetected = { viewModel.dismissSwipeHint() },
                            onTrackClick = { track, context -> viewModel.playTrack(track, context) },
                            onOpenFileClick = onPickAudioFile,
                            onShufflePlay = { pool -> viewModel.playRandomAndShuffle(pool) },
                            onOpenAlbum = { name -> viewModel.openLibraryDetail(LibraryDetail.Album(name)) },
                            onOpenArtist = { name -> viewModel.openLibraryDetail(LibraryDetail.Artist(name)) },
                            onOpenPlaylist = { id -> viewModel.openLibraryDetail(LibraryDetail.PlaylistDetail(id)) },
                            onOpenSpotlight = { category -> viewModel.openLibraryDetail(LibraryDetail.Spotlight(category)) },
                            onPullProgressChanged = { pullProgressState.floatValue = it },
                            isOverlayOpen = isSearchOpen || libraryDetailStack.isNotEmpty()
                        )
                    }
                    AppDestination.FILES -> {
                        FilesScreen(
                            onOpenFilePicker = onPickAudioFile
                        )
                    }
                    AppDestination.EFFECTS -> {
                        EffectsScreen(
                            currentSpeed = playbackSpeed,
                            currentPitch = playbackPitch,
                            isSlowedAndReverb = isSlowedAndReverb,
                            onSetSpeedAndPitch = { speed, pitch -> viewModel.setSpeedAndPitch(speed, pitch) }
                        )
                    }
                    AppDestination.SETTINGS -> {
                        SettingsScreen(
                            useVaporwaveTheme = useVaporwaveTheme,
                            onToggleTheme = { viewModel.setUseVaporwaveTheme(it) },
                            useDarkTheme = useDarkTheme,
                            onToggleDarkTheme = { viewModel.setUseDarkTheme(it) },
                            onRescanLibrary = { viewModel.loadTracks(force = true) }
                        )
                    }
                }
            }
        }

        // Playlist Picker Dialog (opened via the + button on Now Playing)
        if (playlistPickerTarget != null) {
            PlaylistPickerDialog(
                playlists = playlists,
                onSelectPlaylist = { playlistId -> viewModel.addTrackToPlaylist(playlistId) },
                onCreatePlaylist = { name -> viewModel.createPlaylistAndAddTrack(name) },
                onDismiss = { viewModel.dismissPlaylistPicker() }
            )
        }

        // Search card: rendered as a top-level overlay here rather than nested inside the
        // Scaffold's content slot, so it owns its own status bar / IME insets outright instead
        // of inheriting Scaffold's innerPadding (topBar and mini player height) -- mixing the
        // two was the source of the double-reservation bugs the previous version had.
        AnimatedVisibility(
            visible = isSearchOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            SearchScreen(
                query = searchQuery,
                results = tracks,
                currentTrack = currentTrack,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onTrackClick = { track -> viewModel.playTrack(track, tracks) },
                onBack = { viewModel.closeSearch() }
            )
        }

        // Album/artist/playlist/spotlight detail: also a top-level overlay, for the same reason
        // as search -- it rises up over the *entire* screen (status bar and top bar included)
        // rather than being confined to the library tab's own content area beneath the top bar.
        //
        // The content below reads lastNonEmptyStack rather than libraryDetailStack directly:
        // closing pops the stack (eventually to empty, to start the exit animation), but the
        // content lambda keeps recomposing throughout that animation. Reading the live (already
        // popped) state here would blank the overlay out instantly instead of letting it slide
        // away with its content still visible.
        //
        // Mirrored synchronously during composition, and deliberately not snapshot state: doing
        // this from a LaunchedEffect instead meant the overlay's *first* composition still saw an
        // empty stack (effects run after composition), so it composed nothing -- a 0x0 container
        // -- and then animated from that into the real content one pass later, which read as a
        // zoom on the first open of the app. libraryDetailStack is the real state driving
        // recomposition here, so this write must not schedule a pass of its own.
        val stackMirror = remember { object { var value: List<LibraryDetail> = emptyList() } }
        if (libraryDetailStack.isNotEmpty()) stackMirror.value = libraryDetailStack
        val lastNonEmptyStack = stackMirror.value
        AnimatedVisibility(
            visible = libraryDetailStack.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            // A second, inner transition for navigating *within* the overlay while it's already
            // open -- pushing/popping a level animates between them without touching the outer
            // AnimatedVisibility above (which only cares about the overlay's own show/hide).
            // Keyed by the stack's shape (which entries, not their internal fields) so a field
            // changing on the current entry can never accidentally trigger this transition.
            val stackShape = lastNonEmptyStack.map { it.stackIdentity() }
            AnimatedContent(
                targetState = stackShape,
                transitionSpec = {
                    // `using null` because togetherWith otherwise attaches a default
                    // SizeTransform (clipping, spring-animated) -- every level here is a
                    // full-screen page, so there is never a size difference worth animating,
                    // and any that did appear would show up as a clipped zoom.
                    if (targetState.size > initialState.size) {
                        (slideInVertically(initialOffsetY = { it }) + fadeIn()) togetherWith
                            fadeOut() using null
                    } else {
                        fadeIn() togetherWith
                            (slideOutVertically(targetOffsetY = { it }) + fadeOut()) using null
                    }
                },
                label = "libraryDetailStack"
            ) { _ ->
                val detail = lastNonEmptyStack.lastOrNull()
                if (detail != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                            // Rounded top corners, matching the search card -- these overlays
                            // rise up from the bottom the same way, so they should read as the
                            // same kind of lifted card rather than a flat, square-cornered page.
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        ) {
                            val payload = when (detail) {
                                is LibraryDetail.Album -> {
                                    val albumTracks = allTracks.filter { it.album == detail.name }
                                    val artworkUri = albums.find { it.name == detail.name }?.artworkUri
                                    val totalMs = albumTracks.sumOf { it.durationMs }
                                    LibraryDetailPayload(
                                        title = detail.name,
                                        subtitle = "${albumTracks.size} songs • ${formatTotalDuration(totalMs)}",
                                        tracks = albumTracks,
                                        showToolbar = true,
                                        heroArtwork = HeroArtwork(artworkUri, Icons.Default.Album),
                                        // Every row would otherwise repeat the same artwork
                                        // already shown in the hero image above the list.
                                        showTrackArtwork = false
                                    )
                                }
                                is LibraryDetail.Artist -> {
                                    val artistTracks = allTracks.filter { it.artist == detail.name }
                                    val artworkUri = artists.find { it.name == detail.name }?.artworkUri
                                    LibraryDetailPayload(
                                        title = detail.name,
                                        subtitle = "${artistTracks.size} songs",
                                        tracks = artistTracks,
                                        showToolbar = true,
                                        heroArtwork = HeroArtwork(artworkUri, Icons.Default.Person)
                                    )
                                }
                                is LibraryDetail.PlaylistDetail -> {
                                    val playlist = playlists.find { it.id == detail.playlistId }
                                    val playlistTracks = playlist?.trackIds.orEmpty().mapNotNull { id -> allTracks.find { it.id == id } }
                                    LibraryDetailPayload(playlist?.name ?: "Playlist", "${playlistTracks.size} songs", playlistTracks, showToolbar = true)
                                }
                                is LibraryDetail.Spotlight -> {
                                    val categoryTracks = when (detail.category) {
                                        SpotlightCategory.RECENTLY_ADDED -> recentlyAddedTracks
                                        SpotlightCategory.MOST_PLAYED -> mostPlayedTracks
                                        SpotlightCategory.RECENTLY_PLAYED -> recentlyPlayedTracks
                                    }
                                    LibraryDetailPayload(detail.category.label, "${categoryTracks.size} songs", categoryTracks, showToolbar = false)
                                }
                            }
                            DetailTrackList(
                                title = payload.title,
                                subtitle = payload.subtitle,
                                tracks = payload.tracks,
                                currentTrack = currentTrack,
                                onBack = { viewModel.closeLibraryDetail() },
                                onTrackClick = { track -> viewModel.playTrack(track, payload.tracks) },
                                onShufflePlay = { pool -> viewModel.playRandomAndShuffle(pool) },
                                showToolbar = payload.showToolbar,
                                heroArtwork = payload.heroArtwork,
                                showTrackArtwork = payload.showTrackArtwork,
                                modifier = Modifier.statusBarsPadding()
                            )
                        }

                        // The Scaffold's own mini player is hidden behind this overlay (it fully
                        // covers the screen), so without this, playing a track from inside an
                        // album/artist/playlist page would leave no visible player at all until
                        // backing out. Deliberately not its own AnimatedVisibility: this whole Box
                        // already rides the outer slide/fade when the overlay itself opens or
                        // closes, and giving this its own independent enter/exit on top of that
                        // doubled up into a visible second fade right as the overlay closed.
                        if (currentTrack != null) {
                            MiniPlayer(
                                track = currentTrack,
                                isPlaying = isPlaying,
                                progress = progressProvider,
                                onPlayPauseClick = { viewModel.togglePlayPause() },
                                onSkipNextClick = { viewModel.skipToNext() },
                                onExpandClick = { viewModel.setNowPlayingExpanded(true) },
                                onDragDelta = { delta -> dragSheetBy(delta) },
                                onDragStopped = { velocity -> settleSheet(velocity) },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }

        // Full screen Now Playing sheet, positioned by sheetProgress so it can sit anywhere
        // between docked and fully open. Only composed once it starts moving (or is open), to
        // keep it off the critical path while the user is just browsing the library. Rendered
        // last (on top of the search/library-detail overlays above) so expanding it from the
        // mini player docked inside one of those overlays is actually visible above it.
        // derivedStateOf so this only recomposes when the sheet actually appears/disappears --
        // reading sheetProgress.value directly here would invalidate the whole screen on every
        // frame of the drag, which is exactly what the offset lambda below exists to avoid.
        val isSheetVisible by remember { derivedStateOf { sheetProgress.value > 0f } }
        if (currentTrack != null && (isSheetVisible || isNowPlayingExpanded)) {
            NowPlayingSheet(
                modifier = Modifier
                    .nestedScroll(sheetNestedScroll)
                    .offset {
                        // Read inside the lambda so the drag moves the sheet in the layout
                        // phase, without recomposing its (large) content on every frame.
                        IntOffset(0, ((1f - sheetProgress.value) * containerHeightPx).roundToInt())
                    },
                headerDragModifier = Modifier.draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> dragSheetBy(delta) },
                    onDragStopped = { velocity -> settleSheet(velocity) }
                ),
                track = currentTrack,
                isPlaying = isPlaying,
                positionProvider = { playbackPositionState.value },
                durationMs = duration,
                playbackSpeed = playbackSpeed,
                isSlowedAndReverb = isSlowedAndReverb,
                repeatMode = repeatMode,
                isShuffle = isShuffle,
                isFavourite = currentTrack?.id in favouriteTrackIds,
                onToggleFavourite = { currentTrack?.let { viewModel.toggleFavourite(it.id) } },
                onAddToPlaylist = { currentTrack?.let { viewModel.openPlaylistPicker(it) } },
                onDismiss = { viewModel.setNowPlayingExpanded(false) },
                onPlayPause = { viewModel.togglePlayPause() },
                onSeekTo = { viewModel.seekTo(it) },
                onSeekBy = { viewModel.seekBy(it) },
                onSkipNext = { viewModel.skipToNext() },
                onSkipPrevious = { viewModel.skipToPrevious() },
                onToggleRepeat = { viewModel.cycleRepeatMode() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleSlowedAndReverb = { viewModel.toggleSlowedAndReverb() },
                onOpenEffects = {
                    viewModel.setNowPlayingExpanded(false)
                    viewModel.setDestination(AppDestination.EFFECTS)
                }
            )
        }
        }
    }
}

/** A stable identity for a stack entry, so a future field on one of these data classes changing
 *  in place can never be mistaken for a real navigation (push/pop). */
private fun LibraryDetail.stackIdentity(): Any = when (this) {
    is LibraryDetail.Album -> "album:$name"
    is LibraryDetail.Artist -> "artist:$name"
    is LibraryDetail.PlaylistDetail -> "playlist:$playlistId"
    is LibraryDetail.Spotlight -> "spotlight:${category.name}"
}

private fun formatTotalDuration(totalMs: Long): String {
    val totalSeconds = totalMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private data class LibraryDetailPayload(
    val title: String,
    val subtitle: String,
    val tracks: List<AudioTrack>,
    val showToolbar: Boolean,
    val heroArtwork: HeroArtwork? = null,
    val showTrackArtwork: Boolean = true
)

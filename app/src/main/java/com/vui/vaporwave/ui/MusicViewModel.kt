package com.vui.vaporwave.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vui.vaporwave.data.MusicRepository
import com.vui.vaporwave.data.TrackDeleteResult
import com.vui.vaporwave.data.tagging.MetadataFields
import com.vui.vaporwave.data.tagging.TagSaveResult
import com.vui.vaporwave.model.AlbumSummary
import com.vui.vaporwave.model.ArtistSummary
import com.vui.vaporwave.model.AudioTrack
import com.vui.vaporwave.model.ExtendedTrackMetadata
import com.vui.vaporwave.model.PlayStat
import com.vui.vaporwave.model.Playlist
import com.vui.vaporwave.service.VaporwavePlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.abs

enum class AppDestination {
    LIBRARY,
    FILES,
    EFFECTS,
    SETTINGS
}

sealed class MetadataSaveState {
    object Idle : MetadataSaveState()
    object Saving : MetadataSaveState()
    data class Done(val successCount: Int, val totalCount: Int) : MetadataSaveState()
    data class Error(val message: String) : MetadataSaveState()
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _allTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val allTracks: StateFlow<List<AudioTrack>> = _allTracks.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Every derived flow below runs its filtering/grouping/sorting on Dispatchers.Default rather
    // than viewModelScope's main dispatcher -- these are full-library passes, and on a large
    // library doing them on the main thread janks the frame that triggered them.
    // Non-blank queries are debounced (so rapid typing doesn't trigger a full-library scan per
    // keystroke) but the blank case emits immediately, so clearing the search and the initial
    // library load are never held up by the 250ms delay.
    private val debouncedSearchQuery = _searchQuery.transformLatest { query ->
        if (query.isNotEmpty()) {
            delay(250)
        }
        emit(query)
    }

    val filteredTracks: StateFlow<List<AudioTrack>> = combine(_allTracks, debouncedSearchQuery) { tracks, query ->
        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true) ||
                it.formatBadge.contains(query, ignoreCase = true)
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _isSlowedAndReverb = MutableStateFlow(false)
    val isSlowedAndReverb: StateFlow<Boolean> = _isSlowedAndReverb.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _currentDestination = MutableStateFlow(AppDestination.LIBRARY)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _currentLibraryTab = MutableStateFlow(LibraryTab.TRACKS)
    val currentLibraryTab: StateFlow<LibraryTab> = _currentLibraryTab.asStateFlow()

    // Seeded from disk by loadPersistedState() rather than inline, so constructing the ViewModel
    // (which happens during Activity startup) never blocks on SharedPreferences I/O.
    private val _showSwipeHint = MutableStateFlow(false)
    val showSwipeHint: StateFlow<Boolean> = _showSwipeHint.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    // Bumped on every openSearch() call, so the search card can key its one-off "pick a random
    // placeholder" logic on this instead of on its own composition lifetime -- AnimatedVisibility
    // can keep the card's composition alive across a close immediately followed by a reopen (the
    // exit transition hasn't finished tearing it down yet), which otherwise meant the placeholder
    // never actually re-rolled and looked stuck on the same line every time.
    private val _searchOpenSequence = MutableStateFlow(0)
    val searchOpenSequence: StateFlow<Int> = _searchOpenSequence.asStateFlow()

    // A real back stack (not just a single nullable value): opening an album from inside an
    // artist's Albums tab pushes on top of that artist entry rather than replacing it, so
    // closing it pops back to the artist -- at whichever tab it was showing -- instead of
    // closing the whole overlay outright.
    private val _libraryDetailStack = MutableStateFlow<List<LibraryDetail>>(emptyList())
    val libraryDetailStack: StateFlow<List<LibraryDetail>> = _libraryDetailStack.asStateFlow()

    private val _useVaporwaveTheme = MutableStateFlow(true)
    val useVaporwaveTheme: StateFlow<Boolean> = _useVaporwaveTheme.asStateFlow()

    private val _useDarkTheme = MutableStateFlow(true)
    val useDarkTheme: StateFlow<Boolean> = _useDarkTheme.asStateFlow()

    private val _favouriteTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    val favouriteTrackIds: StateFlow<Set<Long>> = _favouriteTrackIds.asStateFlow()

    val favouriteTracks: StateFlow<List<AudioTrack>> = combine(_allTracks, _favouriteTrackIds) { tracks, ids ->
        tracks.filter { ids.contains(it.id) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _playlistPickerTarget = MutableStateFlow<AudioTrack?>(null)
    val playlistPickerTarget: StateFlow<AudioTrack?> = _playlistPickerTarget.asStateFlow()

    /** Id of the playlist whose "choose artwork" track picker is currently open, if any. */
    private val _artworkPickerPlaylistId = MutableStateFlow<Long?>(null)
    val artworkPickerPlaylistId: StateFlow<Long?> = _artworkPickerPlaylistId.asStateFlow()

    private val _trackDetailsTarget = MutableStateFlow<AudioTrack?>(null)
    val trackDetailsTarget: StateFlow<AudioTrack?> = _trackDetailsTarget.asStateFlow()

    private val _metadataEditTarget = MutableStateFlow<MetadataEditTarget?>(null)
    val metadataEditTarget: StateFlow<MetadataEditTarget?> = _metadataEditTarget.asStateFlow()

    private val _metadataSaveState = MutableStateFlow<MetadataSaveState>(MetadataSaveState.Idle)
    val metadataSaveState: StateFlow<MetadataSaveState> = _metadataSaveState.asStateFlow()

    // Surfaced to MainActivity, which is the only place that can actually launch an IntentSender
    // (createWriteRequest's consent dialog, or the recoverable-access dialog Android hands back
    // when a write is denied) -- the coroutine driving saveMetadata suspends on
    // writeRequestContinuation until MainActivity reports back via onWriteRequestResult.
    private val _pendingWriteRequest = MutableStateFlow<IntentSenderRequest?>(null)
    val pendingWriteRequest: StateFlow<IntentSenderRequest?> = _pendingWriteRequest.asStateFlow()
    private var writeRequestContinuation: CancellableContinuation<Boolean>? = null

    val albums: StateFlow<List<AlbumSummary>> = _allTracks.map { tracks ->
        tracks.groupBy { it.album }
            .map { (album, tracksInAlbum) ->
                AlbumSummary(
                    name = album,
                    artist = tracksInAlbum.first().artist,
                    artworkUri = tracksInAlbum.firstOrNull { it.artworkUri != null }?.artworkUri,
                    trackCount = tracksInAlbum.size,
                    latestDateAddedMs = tracksInAlbum.maxOf { it.dateAddedMs }
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val artists: StateFlow<List<ArtistSummary>> = _allTracks.map { tracks ->
        tracks.groupBy { it.artist }
            .map { (artist, tracksByArtist) ->
                ArtistSummary(
                    name = artist,
                    trackCount = tracksByArtist.size,
                    // Borrow the cover art of one of this artist's albums since standalone artist photos aren't available.
                    artworkUri = tracksByArtist.firstOrNull { it.artworkUri != null }?.artworkUri,
                    latestDateAddedMs = tracksByArtist.maxOf { it.dateAddedMs }
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Not exposed publicly: the UI only ever consumes the mostPlayed/recentlyPlayed views below.
    private val _playStats = MutableStateFlow<Map<Long, PlayStat>>(emptyMap())

    val recentlyAddedTracks: StateFlow<List<AudioTrack>> = _allTracks.map { tracks ->
        tracks.filter { it.dateAddedMs > 0L }
            .sortedByDescending { it.dateAddedMs }
            .take(200)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Ranked by total estimated playtime (play count x duration) rather than raw play count,
    // so a handful of plays of a long track can outrank many plays of a short one.
    val mostPlayedTracks: StateFlow<List<AudioTrack>> = combine(_allTracks, _playStats) { tracks, stats ->
        tracks
            .filter { (stats[it.id]?.playCount ?: 0) > 0 }
            .sortedByDescending { stats[it.id]!!.playCount.toLong() * it.durationMs }
            .take(125)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentlyPlayedTracks: StateFlow<List<AudioTrack>> = combine(_allTracks, _playStats) { tracks, stats ->
        tracks
            .filter { (stats[it.id]?.lastPlayedAt ?: 0L) > 0L }
            .sortedByDescending { stats[it.id]!!.lastPlayedAt }
            .take(125)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var progressJob: Job? = null
    private var loadJob: Job? = null
    private var hasLoadedTracks = false
    private var currentPlaylist: List<AudioTrack> = emptyList()

    // Kept in lockstep with every _allTracks.value write (see setAllTracks) so
    // onMediaItemTransition can resolve the playing track by stable numeric id in O(1)
    // instead of linear-scanning and string-comparing content URIs.
    private var trackById: Map<Long, AudioTrack> = emptyMap()

    private fun setAllTracks(tracks: List<AudioTrack>) {
        _allTracks.value = tracks
        trackById = tracks.associateBy { it.id }
    }

    init {
        connectToService()
        loadTracks()
        loadPersistedState()
    }

    /** Seeds favourites, playlists, play stats and the swipe hint from disk, off the main thread. */
    private fun loadPersistedState() {
        viewModelScope.launch {
            _favouriteTrackIds.value = repository.loadFavouriteIds()
            _playlists.value = repository.loadPlaylists()
            _playStats.value = repository.loadPlayStats()
            _showSwipeHint.value = !repository.hasSeenSwipeHint()
        }
    }

    private fun connectToService() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, VaporwavePlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                setupPlayerListener(controller)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener(player: Player) {
        _isPlaying.value = player.isPlaying
        _playbackSpeed.value = player.playbackParameters.speed
        _playbackPitch.value = player.playbackParameters.pitch
        _repeatMode.value = player.repeatMode
        _isShuffleEnabled.value = player.shuffleModeEnabled

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0L)
                    _playbackPosition.value = player.currentPosition.coerceAtLeast(0L)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaId?.toLongOrNull()
                val track = trackId?.let { trackById[it] }
                _currentTrack.value = track
                _duration.value = player.duration.coerceAtLeast(0L)
                track?.let { recordPlay(it.id) }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
                _playbackPitch.value = playbackParameters.pitch
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffleEnabled.value = shuffleModeEnabled
            }
        })

        if (player.isPlaying) {
            startProgressTracker()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _playbackPosition.value = controller.currentPosition.coerceAtLeast(0L)
                    if (controller.duration > 0) {
                        _duration.value = controller.duration
                    }
                }
                delay(250)
            }
        }
    }

    private fun recordPlay(trackId: Long) {
        val existing = _playStats.value[trackId]
        val updated = _playStats.value + (trackId to PlayStat(
            trackId = trackId,
            playCount = (existing?.playCount ?: 0) + 1,
            lastPlayedAt = System.currentTimeMillis()
        ))
        _playStats.value = updated
        viewModelScope.launch { repository.savePlayStats(updated) }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
        mediaController?.let {
            _playbackPosition.value = it.currentPosition.coerceAtLeast(0L)
        }
    }

    /**
     * Scans the device library. The Activity requests permissions on every start and calls this
     * from the result callback on top of the call in init, so without the guards below every
     * launch runs the whole MediaStore scan twice and discards one result.
     *
     * [force] is for the explicit "rescan library" action, which must always re-read the disk.
     */
    fun loadTracks(force: Boolean = false) {
        if (loadJob?.isActive == true) return
        if (hasLoadedTracks && !force) return
        loadJob = viewModelScope.launch {
            val localTracks = repository.scanLocalTracks()
            applyLoadedTracks(localTracks)
            // Only counts as loaded if it actually returned something: on a first-ever launch the
            // init scan runs before the permission is granted and legitimately comes back empty,
            // and that must not suppress the rescan the permission callback then triggers.
            hasLoadedTracks = localTracks.isNotEmpty()
        }
    }

    private fun applyLoadedTracks(tracks: List<AudioTrack>) {
        setAllTracks(tracks)
        if (_currentTrack.value == null && tracks.isNotEmpty()) {
            _currentTrack.value = tracks.first()
            currentPlaylist = tracks
        }
    }

    /**
     * Suspends until the MediaController has finished connecting (or failed to).
     */
    private suspend fun awaitMediaController(): MediaController? {
        mediaController?.let { return it }
        val future = controllerFuture ?: return null
        return suspendCancellableCoroutine { cont ->
            future.addListener({
                val controller = try {
                    future.get()
                } catch (e: Exception) {
                    null
                }
                cont.resume(controller) { _, _, _ -> }
            }, MoreExecutors.directExecutor())
        }
    }

    fun playTrack(track: AudioTrack, playlist: List<AudioTrack> = _allTracks.value) {
        val controller = mediaController ?: return
        currentPlaylist = playlist
        _currentTrack.value = track

        val startIndex = playlist.indexOfFirst { it.contentUri == track.contentUri }.coerceAtLeast(0)

        val mediaItems = playlist.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setArtworkUri(item.artworkUri)
                        .build()
                )
                .build()
        }

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.mediaItemCount == 0 && _currentTrack.value != null) {
                _currentTrack.value?.let { playTrack(it) }
            } else {
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val clamped = positionMs.coerceIn(0L, _duration.value.coerceAtLeast(0L))
        controller.seekTo(clamped)
        _playbackPosition.value = clamped
    }

    fun seekBy(offsetMs: Long) {
        val controller = mediaController ?: return
        // controller.duration is C.TIME_UNSET (negative) until the player reaches STATE_READY;
        // fall back to the last known duration, or skip the upper clamp entirely if neither is known yet.
        val knownDuration = controller.duration.takeIf { it > 0 } ?: _duration.value.takeIf { it > 0 }
        val rawTarget = controller.currentPosition + offsetMs
        val target = if (knownDuration != null) rawTarget.coerceIn(0L, knownDuration) else rawTarget.coerceAtLeast(0L)
        controller.seekTo(target)
        _playbackPosition.value = target
    }

    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    // OFF -> ONE -> ALL -> OFF -- matches the Now Playing repeat icon's own animated sequence
    // (bare "1" -> "1" ringed by arrows -> arrows only -> back to bare "1").
    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val newMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = newMode
        _repeatMode.value = newMode
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newShuffle = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newShuffle
        _isShuffleEnabled.value = newShuffle
    }

    /**
     * Picks a random track from [pool], starts playing it, and turns shuffle mode on
     * (used by the dice/shuffle button on the Tracks tab).
     */
    fun playRandomAndShuffle(pool: List<AudioTrack>) {
        if (pool.isEmpty()) return
        val randomTrack = pool.random()
        playTrack(randomTrack, pool)
        mediaController?.shuffleModeEnabled = true
        _isShuffleEnabled.value = true
    }

    /**
     * Sets playback speed and pitch for real-time sound sculpting.
     */
    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        val controller = mediaController ?: return
        val clampedSpeed = speed.coerceIn(0.25f, 2.5f)
        val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
        controller.playbackParameters = PlaybackParameters(clampedSpeed, clampedPitch)
        _playbackSpeed.value = clampedSpeed
        _playbackPitch.value = clampedPitch
        _isSlowedAndReverb.value = clampedSpeed.approxEquals(0.85f) && clampedPitch.approxEquals(0.85f)
    }

    /**
     * Toggles the iconic Vaporwave Slowed + Pitch Mode (0.85x speed, 0.85x pitch).
     */
    fun toggleSlowedAndReverb() {
        if (_isSlowedAndReverb.value) {
            setSpeedAndPitch(1.0f, 1.0f)
            _isSlowedAndReverb.value = false
        } else {
            setSpeedAndPitch(0.85f, 0.85f)
            _isSlowedAndReverb.value = true
        }
    }

    /**
     * Directly resolves and plays any external audio file chosen via SAF file picker or intent.
     */
    fun openAndPlayUri(uri: Uri) {
        viewModelScope.launch {
            val track = repository.resolveTrackFromUri(uri)
            if (track != null) {
                val updatedList = listOf(track) + _allTracks.value.filter { it.contentUri != track.contentUri }
                setAllTracks(updatedList)
                // Wait for the MediaController to finish connecting so a cold-start "open with"
                // launch doesn't silently drop playback if metadata resolution wins the race.
                awaitMediaController()
                playTrack(track, updatedList)
                _isNowPlayingExpanded.value = true
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openSearch() {
        _isSearchOpen.value = true
        _searchOpenSequence.value++
    }

    /** Closes the search card and clears the query so the library isn't left filtered behind it. */
    fun closeSearch() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
    }

    fun openLibraryDetail(detail: LibraryDetail) {
        _libraryDetailStack.value = _libraryDetailStack.value + detail
    }

    /** Pops the top of the stack -- closes the overlay outright only once it empties out. */
    fun closeLibraryDetail() {
        _libraryDetailStack.value = _libraryDetailStack.value.dropLast(1)
    }

    /** Updates the tab index recorded on the top-of-stack artist entry, if there is one. */
    fun setDestination(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun setCurrentLibraryTab(tab: LibraryTab) {
        _currentLibraryTab.value = tab
    }

    fun dismissSwipeHint() {
        if (_showSwipeHint.value) {
            _showSwipeHint.value = false
            viewModelScope.launch { repository.setSeenSwipeHint() }
        }
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
    }

    fun setUseVaporwaveTheme(useVaporwave: Boolean) {
        _useVaporwaveTheme.value = useVaporwave
    }

    fun setUseDarkTheme(useDark: Boolean) {
        _useDarkTheme.value = useDark
    }

    fun toggleFavourite(trackId: Long) {
        val updated = _favouriteTrackIds.value.toMutableSet()
        if (!updated.add(trackId)) {
            updated.remove(trackId)
        }
        _favouriteTrackIds.value = updated
        viewModelScope.launch { repository.saveFavouriteIds(updated) }
    }

    fun openPlaylistPicker(track: AudioTrack) {
        _playlistPickerTarget.value = track
    }

    fun dismissPlaylistPicker() {
        _playlistPickerTarget.value = null
    }

    fun openTrackDetails(track: AudioTrack) {
        _trackDetailsTarget.value = track
    }

    fun closeTrackDetails() {
        _trackDetailsTarget.value = null
    }

    /** On-demand fetch backing Track Details' Genre/Recording Date/Path -- see [MusicRepository.fetchExtendedMetadata]. */
    suspend fun fetchExtendedMetadata(track: AudioTrack): ExtendedTrackMetadata =
        repository.fetchExtendedMetadata(track)

    fun openMetadataEditor(target: MetadataEditTarget) {
        _metadataEditTarget.value = target
    }

    fun closeMetadataEditor() {
        _metadataEditTarget.value = null
        _metadataSaveState.value = MetadataSaveState.Idle
    }

    fun resetMetadataSaveState() {
        _metadataSaveState.value = MetadataSaveState.Idle
    }

    /**
     * Writes [fields] into the file(s) behind [target] -- one track, or (for an album/artist edit)
     * every track under it. Requests write access first where the platform supports asking for it
     * up front (API 30+'s createWriteRequest), and otherwise falls back to the per-file recoverable
     * access prompt Android hands back when a write is denied (API 29). Either way the actual
     * IntentSender launch has to happen from MainActivity (only an Activity can do that), so this
     * suspends on [pendingWriteRequest] being resolved via [onWriteRequestResult].
     */
    fun saveMetadata(target: MetadataEditTarget, fields: MetadataFields) {
        viewModelScope.launch {
            _metadataSaveState.value = MetadataSaveState.Saving
            val tracks = when (target) {
                is MetadataEditTarget.Track -> listOf(target.track)
                is MetadataEditTarget.Album -> target.tracks
            }
            val uris = tracks.mapNotNull { it.contentUri }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
                val pending = MediaStore.createWriteRequest(getApplication<Application>().contentResolver, uris)
                val granted = awaitIntentSender(pending.intentSender)
                if (!granted) {
                    _metadataSaveState.value = MetadataSaveState.Error("Permission denied")
                    return@launch
                }
            }

            var successCount = 0
            var lastError: String? = null
            for (track in tracks) {
                // An album batch edit only ever changes the fields shared across the whole album --
                // title and track/disc number are always that one track's own. Without this,
                // saving with those fields left blank in the editor (they're hidden there for
                // exactly this reason) would blank those tags on every track in the album instead
                // of leaving them alone.
                val perTrackFields = when (target) {
                    is MetadataEditTarget.Track -> fields
                    is MetadataEditTarget.Album -> fields.copy(
                        title = track.title,
                        trackNumber = track.trackNumber,
                        discNumber = track.discNumber
                    )
                }
                var result = repository.saveTrackMetadata(track, perTrackFields)
                if (result is TagSaveResult.NeedsPermission) {
                    val granted = awaitIntentSender(result.intentSender)
                    result = if (granted) repository.saveTrackMetadata(track, perTrackFields)
                    else TagSaveResult.Failure("${track.title}: permission denied")
                }
                when (result) {
                    is TagSaveResult.Success -> successCount++
                    is TagSaveResult.Failure -> lastError = result.message
                    is TagSaveResult.NeedsPermission -> lastError = "${track.title}: permission denied"
                }
            }

            if (successCount > 0) loadTracks(force = true)
            _metadataSaveState.value = when {
                lastError == null -> MetadataSaveState.Done(successCount, tracks.size)
                successCount > 0 -> MetadataSaveState.Error("Saved $successCount of ${tracks.size} -- $lastError")
                else -> MetadataSaveState.Error(lastError)
            }
        }
    }

    /** Called by MainActivity once it's launched the IntentSender from [pendingWriteRequest] and gotten a result. */
    fun onWriteRequestResult(granted: Boolean) {
        _pendingWriteRequest.value = null
        writeRequestContinuation?.let { if (it.isActive) it.resume(granted) { _, _, _ -> } }
        writeRequestContinuation = null
    }

    private suspend fun awaitIntentSender(intentSender: android.content.IntentSender): Boolean =
        suspendCancellableCoroutine { continuation ->
            writeRequestContinuation = continuation
            _pendingWriteRequest.value = IntentSenderRequest.Builder(intentSender).build()
        }

    /**
     * Deletes [track]'s file, asking for write access first if it's needed -- see
     * [MusicRepository.deleteTrack]. If [track] was the one currently playing, moves on to the
     * next track (or just leaves playback stopped if it was the only one) and collapses the Now
     * Playing sheet, since it's no longer showing anything that still exists.
     */
    fun deleteTrack(track: AudioTrack) {
        viewModelScope.launch {
            var result = repository.deleteTrack(track)
            if (result is TrackDeleteResult.NeedsPermission) {
                val granted = awaitIntentSender(result.intentSender)
                result = if (granted) repository.deleteTrack(track) else TrackDeleteResult.Failure("Permission denied")
            }
            when (result) {
                is TrackDeleteResult.Success -> {
                    if (_currentTrack.value?.id == track.id) {
                        skipToNext()
                        setNowPlayingExpanded(false)
                    }
                    loadTracks(force = true)
                }
                is TrackDeleteResult.Failure -> {
                    Toast.makeText(getApplication(), result.message, Toast.LENGTH_SHORT).show()
                }
                is TrackDeleteResult.NeedsPermission -> Unit // unreachable: retried above
            }
        }
    }

    /**
     * Creates a new playlist and adds the pending track to it -- unless a playlist with the same
     * name (case-insensitive) already exists, in which case the track is added to that existing
     * one instead. The picker dialog already disables its own "Create" button on a duplicate name,
     * so this is really only a safety net for anything that calls straight through without going
     * via that UI.
     */
    fun createPlaylistAndAddTrack(name: String) {
        val track = _playlistPickerTarget.value ?: return
        val trimmedName = name.trim().ifBlank { "New Playlist" }
        val existing = _playlists.value.firstOrNull { it.name.equals(trimmedName, ignoreCase = true) }
        if (existing != null) {
            addTrackToPlaylist(existing.id)
            return
        }
        val newPlaylist = Playlist(
            id = System.currentTimeMillis(),
            name = trimmedName,
            trackIds = listOf(track.id)
        )
        val updated = _playlists.value + newPlaylist
        _playlists.value = updated
        viewModelScope.launch { repository.savePlaylists(updated) }
        _playlistPickerTarget.value = null
    }

    fun addTrackToPlaylist(playlistId: Long) {
        val track = _playlistPickerTarget.value ?: return
        val updated = _playlists.value.map { playlist ->
            if (playlist.id == playlistId && !playlist.trackIds.contains(track.id)) {
                playlist.copy(trackIds = playlist.trackIds + track.id)
            } else {
                playlist
            }
        }
        _playlists.value = updated
        viewModelScope.launch { repository.savePlaylists(updated) }
        _playlistPickerTarget.value = null
    }

    fun openArtworkPicker(playlistId: Long) {
        _artworkPickerPlaylistId.value = playlistId
    }

    fun dismissArtworkPicker() {
        _artworkPickerPlaylistId.value = null
    }

    fun setPlaylistArtwork(playlistId: Long, trackId: Long) {
        val updated = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(artworkTrackId = trackId) else playlist
        }
        _playlists.value = updated
        viewModelScope.launch { repository.savePlaylists(updated) }
        _artworkPickerPlaylistId.value = null
    }

    override fun onCleared() {
        stopProgressTracker()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}

/**
 * Tolerant float comparison for playback speed/pitch, which can drift by an ULP or two
 * from slider step accumulation or ExoPlayer's internal Sonic audio-processor rounding.
 */
private fun Float.approxEquals(other: Float, epsilon: Float = 0.01f) = abs(this - other) < epsilon

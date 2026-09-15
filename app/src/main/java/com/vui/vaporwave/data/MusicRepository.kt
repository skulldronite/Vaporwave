package com.vui.vaporwave.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.vui.vaporwave.data.lyrics.EmbeddedLyricsReader
import com.vui.vaporwave.data.lyrics.LrcParser
import com.vui.vaporwave.data.tagging.AudioTagWriter
import com.vui.vaporwave.data.tagging.MetadataFields
import com.vui.vaporwave.data.tagging.TagSaveResult
import com.vui.vaporwave.model.AudioTrack
import com.vui.vaporwave.model.ExtendedTrackMetadata
import com.vui.vaporwave.model.LyricLine
import com.vui.vaporwave.model.LyricsResult
import com.vui.vaporwave.model.PlayStat
import com.vui.vaporwave.model.Playlist
import com.vui.vaporwave.model.RecentAudioEntry
import com.vui.vaporwave.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * MusicRepository manages audio track discovery from MediaStore
 * and direct file inspection via Storage Access Framework.
 */
class MusicRepository(private val context: Context) {

    /**
     * Scans local audio storage via MediaStore.
     * Compatible with Android 10 (API 29) through Android 16 (API 36).
     */
    suspend fun scanLocalTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val trackCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Unknown Title"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val duration = it.getLong(durationCol)
                    val albumId = it.getLong(albumIdCol)
                    val mimeType = it.getString(mimeCol)
                    val size = it.getLong(sizeCol)
                    // DATE_ADDED is stored in seconds since epoch; normalize to milliseconds.
                    val dateAddedMs = it.getLong(dateAddedCol) * 1000L
                    // MediaStore packs disc number into the thousands place (e.g. disc 2 track 3
                    // is stored as 2003), so isolate the actual track number with % 1000 and the
                    // disc number with integer division -- 0 for a plain, un-prefixed track
                    // number, meaning a single-disc (or untagged) release.
                    val trackColValue = it.getInt(trackCol)
                    val trackNumber = trackColValue % 1000
                    val discNumber = trackColValue / 1000
                    val year = it.getInt(yearCol)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    // Deliberately the ALBUMS table row URI, not the track's own content URI.
                    // MediaProvider only serves a thumbnail (via ContentResolver.loadThumbnail,
                    // which is the sole API used to open this artworkUri -- see
                    // AudioThumbnailFetcher and ContentUriBitmapLoader) for rows in tables it
                    // considers thumbnail-eligible, which for audio means the album, not the
                    // individual track. openInputStream/getType directly on this same URI does
                    // fail ("Unknown URL") since it's not a generically-openable file -- but that
                    // failure is specific to that different code path, not to loadThumbnail's
                    // openTypedAssetFileDescriptor request, which is what MediaProvider actually
                    // implements album-art serving through.
                    val artworkUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        albumId
                    )

                    tracks.add(
                        AudioTrack(
                            id = id,
                            title = title,
                            artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                            album = if (album == "<unknown>") "Unknown Album" else album,
                            durationMs = duration,
                            contentUri = contentUri,
                            artworkUri = artworkUri,
                            mimeType = mimeType,
                            sizeBytes = size,
                            isDemoTrack = false,
                            dateAddedMs = dateAddedMs,
                            trackNumber = trackNumber,
                            discNumber = discNumber,
                            year = year
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tracks
    }

    /**
     * Resolves an AudioTrack from any content or file URI selected via SAF / document picker.
     */
    suspend fun resolveTrackFromUri(uri: Uri): AudioTrack? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs: Long = 0L
        var mimeType: String? = null
        var sampleRate: Int = 44100
        var bitrate: Int = 0
        var trackNumber = 0
        var resolvedOk = false
        var embeddedArt: ByteArray? = null

        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val srStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            sampleRate = srStr?.toIntOrNull() ?: 44100
            val brStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            bitrate = (brStr?.toIntOrNull() ?: 0) / 1000
            // Tag value is often "track/total" (e.g. "3/12"); only the part before the slash is
            // the actual track number.
            val trackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            trackNumber = trackStr?.substringBefore('/')?.toIntOrNull() ?: 0
            // SAF/"Open with" files have no MediaStore album-art row (that lookup is scoped to
            // the scanned library only, see scanLocalTracks below) -- their tags' own embedded
            // cover, if any, is the only artwork source available for them.
            embeddedArt = retriever.embeddedPicture
            resolvedOk = true
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        // Genuinely failed to read this URI as audio -- surface null rather than a bogus entry.
        if (!resolvedOk) return@withContext null

        // Fallback title from OpenableColumns
        if (title.isNullOrBlank()) {
            title = getDisplayName(uri) ?: "Audio File"
        }

        val id = stableIdFor(uri)

        AudioTrack(
            id = id,
            title = title,
            artist = artist ?: "External File",
            album = album ?: "Local Storage",
            durationMs = durationMs,
            contentUri = uri,
            artworkUri = embeddedArt?.let { cacheEmbeddedArtwork(id, it) },
            mimeType = mimeType ?: context.contentResolver.getType(uri) ?: "audio/*",
            sampleRateHz = sampleRate,
            bitrateKbps = bitrate,
            isDemoTrack = false,
            trackNumber = trackNumber,
            sizeBytes = getFileSize(uri) ?: 0L
        )
    }

    /**
     * Caches an externally opened file's embedded cover art to this app's own cache dir so it can
     * be handed to Coil as a plain file Uri, same as every other artworkUri in the app. Named by
     * the track's own stable id, so re-opening the same file overwrites rather than accumulates a
     * duplicate on disk.
     */
    private fun cacheEmbeddedArtwork(trackId: Long, bytes: ByteArray): Uri? {
        return try {
            val dir = File(context.cacheDir, "external_artwork").apply { mkdirs() }
            val file = File(dir, "$trackId.jpg")
            file.outputStream().use { it.write(bytes) }
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads the handful of extra fields Track Details shows but the full-library scan doesn't
     * bother with (see [ExtendedTrackMetadata]). Opens the file with MediaMetadataRetriever, so
     * this is only ever called for one track at a time when its details screen is opened, never
     * across the whole library.
     */
    suspend fun fetchExtendedMetadata(track: AudioTrack): ExtendedTrackMetadata = withContext(Dispatchers.IO) {
        var genre: String? = null
        var recordingDate: String? = null
        var filePath: String? = null
        var albumArtist: String? = null
        var bitrateKbps = 0

        val uri = track.contentUri
        if (uri != null) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                recordingDate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                val brStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                bitrateKbps = (brStr?.toIntOrNull() ?: 0) / 1000
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    retriever.release()
                } catch (ignored: Exception) {}
            }

            filePath = resolveFilePath(uri)
        }

        ExtendedTrackMetadata(
            genre = genre,
            recordingDate = recordingDate,
            filePath = filePath,
            albumArtist = albumArtist,
            bitrateKbps = bitrateKbps
        )
    }

    /**
     * The real on-disk path behind a track's content Uri, via MediaStore's (deprecated but still
     * functional) DATA column. Only resolvable for MediaStore-scanned tracks -- externally opened
     * files (SAF) have no on-disk path the app is given direct access to, just the content Uri
     * itself.
     */
    private fun resolveFilePath(uri: Uri): String? {
        if (uri.scheme != "content") return null
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Synced lyrics from a sibling .lrc file take priority; if none is found, falls back to
     * unsynced lyrics embedded in the track's own tags (ID3 USLT / FLAC Vorbis comment / MP4
     * ©lyr -- see the `data.lyrics` readers). Null means neither exists.
     *
     * The .lrc lookup itself has three fallbacks in order, each only reached if the previous
     * returned nothing:
     * 1. A direct java.io.File read (works on plenty of devices, e.g. plain Android 12+ with
     *    READ_MEDIA_AUDIO).
     * 2. MediaProvider itself (query MediaStore.Files by exact path, open via its content:// Uri,
     *    scanning the file in first if it wasn't already indexed). Confirmed this alone still
     *    isn't enough on a real device: MediaStore.Files is the *generic* files collection, not
     *    covered by READ_MEDIA_AUDIO (which only grants the Audio-typed collection) -- opening
     *    even a validly-resolved Uri from it throws SecurityException without further permission.
     * 3. A user-granted SAF folder tree (see [saveLyricsTreeUri]/[loadLyricsTreeUri]), searched
     *    for a file with the same name. This is the only one of the three that's actually
     *    guaranteed to work under scoped storage for a non-owned, non-audio file -- the first two
     *    are opportunistic and simply do nothing (not crash) when blocked.
     */
    suspend fun fetchLyrics(track: AudioTrack): LyricsResult? = withContext(Dispatchers.IO) {
        val uri = track.contentUri
        Log.d(LYRICS_TAG, "fetchLyrics: track=${track.title} contentUri=$uri")
        if (uri == null) {
            Log.d(LYRICS_TAG, "fetchLyrics: no contentUri, giving up")
            return@withContext null
        }

        val syncedLines = fetchSyncedLyrics(uri)
        if (syncedLines != null) return@withContext LyricsResult.Synced(syncedLines)

        val embeddedText = fetchEmbeddedLyrics(uri, track.formatBadge)
        Log.d(LYRICS_TAG, "fetchLyrics: embedded -> ${if (embeddedText != null) "${embeddedText.length} chars" else "null"}")
        return@withContext embeddedText?.let { LyricsResult.Plain(it) }
    }

    private suspend fun fetchSyncedLyrics(uri: Uri): List<LyricLine>? {
        val filePath = resolveFilePath(uri)
        Log.d(LYRICS_TAG, "fetchSyncedLyrics: resolveFilePath -> $filePath")
        if (filePath == null) return null
        val lrcPath = filePath.substringBeforeLast('.', filePath) + ".lrc"
        Log.d(LYRICS_TAG, "fetchSyncedLyrics: looking for lrc at $lrcPath")

        val viaFile = readLrcViaFile(lrcPath)
        Log.d(LYRICS_TAG, "fetchSyncedLyrics: readLrcViaFile -> ${if (viaFile != null) "${viaFile.length} chars" else "null"}")
        val viaMediaStore = viaFile ?: readLrcViaMediaStore(lrcPath)
        if (viaFile == null) {
            Log.d(LYRICS_TAG, "fetchSyncedLyrics: readLrcViaMediaStore -> ${if (viaMediaStore != null) "${viaMediaStore.length} chars" else "null"}")
        }

        val lrcText = viaMediaStore ?: readLrcViaSafTree(lrcPath)
        if (viaMediaStore == null) {
            Log.d(LYRICS_TAG, "fetchSyncedLyrics: readLrcViaSafTree -> ${if (lrcText != null) "${lrcText.length} chars" else "null"}")
        }

        val result = lrcText?.let { text -> LrcParser.parse(text).takeIf { it.isNotEmpty() } }
        Log.d(LYRICS_TAG, "fetchSyncedLyrics: parsed -> ${result?.size ?: 0} lines")
        return result
    }

    /**
     * Only reads a bounded prefix of the audio file rather than the whole thing -- this runs
     * automatically on every track change (to know upfront whether the tap-to-view-lyrics gesture
     * should do anything), so pulling a multi-hundred-MB FLAC entirely into memory just to check
     * its tags would be wasteful. All three readers' tag structures (ID3v2 header, FLAC metadata
     * blocks, MP4 moov) live at the start of the file for the overwhelming majority of real
     * encoders, so this prefix comfortably covers them; the rare moov-at-the-end MP4 is a known
     * gap, acceptable for a fallback that only matters when there's no .lrc.
     */
    private fun fetchEmbeddedLyrics(uri: Uri, formatBadge: String): String? {
        val prefix = readPrefixBytes(uri, EMBEDDED_LYRICS_PREFIX_BYTES) ?: return null
        return EmbeddedLyricsReader.read(prefix, formatBadge)
    }

    private fun readPrefixBytes(uri: Uri, maxBytes: Int): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var totalRead = 0
            while (totalRead < maxBytes) {
                val toRead = minOf(chunk.size, maxBytes - totalRead)
                val n = input.read(chunk, 0, toRead)
                if (n <= 0) break
                buffer.write(chunk, 0, n)
                totalRead += n
            }
            buffer.toByteArray()
        }
    } catch (e: Exception) {
        Log.d(LYRICS_TAG, "readPrefixBytes: threw ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    private fun readLrcViaFile(path: String): String? = try {
        val file = File(path)
        Log.d(LYRICS_TAG, "readLrcViaFile: exists=${file.exists()} canRead=${file.canRead()} isFile=${file.isFile}")
        file.takeIf { it.isFile }?.readText()
    } catch (e: Exception) {
        Log.d(LYRICS_TAG, "readLrcViaFile: threw ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    private suspend fun readLrcViaMediaStore(path: String): String? {
        val firstUri = queryLrcContentUri(path)
        Log.d(LYRICS_TAG, "readLrcViaMediaStore: initial query -> $firstUri")
        firstUri?.let { return readTextFromUri(it) }

        // Not indexed yet (a .lrc dropped in after the last media scan, or an OEM scanner that
        // skips unrecognized extensions entirely) -- ask MediaScannerConnection to index just
        // this one file. Critically, use the Uri the scan callback hands back directly instead
        // of re-querying: on a removable/SD-card volume, that Uri's authority is the volume's
        // own id (e.g. "6261-6563"), not "external" -- and on API 29 (Android 10), the
        // MediaStore.VOLUME_EXTERNAL ("external") collection only covers *primary* storage, so a
        // re-query against it would silently miss anything the scanner just indexed on the SD
        // card (confirmed on a Samsung Android 10 device: the scan succeeded and returned a
        // 6261-6563-authority Uri, but a follow-up "external" query still came back empty).
        val scannedUri = suspendCancellableCoroutine<Uri?> { continuation ->
            MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, resultUri ->
                Log.d(LYRICS_TAG, "readLrcViaMediaStore: scanFile callback resultUri=$resultUri")
                if (continuation.isActive) continuation.resume(resultUri)
            }
        }
        scannedUri?.let { uri -> readTextFromUri(uri)?.let { return it } }

        // Last resort: the initial query only checked MediaStore.VOLUME_EXTERNAL, which misses
        // secondary volumes pre-API 30 -- try every volume MediaStore actually knows about.
        return queryLrcContentUriAcrossVolumes(path)?.let { readTextFromUri(it) }
    }

    private fun queryLrcContentUri(path: String): Uri? = queryLrcContentUriInVolume(path, MediaStore.VOLUME_EXTERNAL)

    private fun queryLrcContentUriAcrossVolumes(path: String): Uri? {
        val volumes = try {
            MediaStore.getExternalVolumeNames(context)
        } catch (e: Exception) {
            Log.d(LYRICS_TAG, "queryLrcContentUriAcrossVolumes: threw ${e.javaClass.simpleName}: ${e.message}")
            emptySet()
        }
        for (volume in volumes) {
            queryLrcContentUriInVolume(path, volume)?.let {
                Log.d(LYRICS_TAG, "queryLrcContentUriAcrossVolumes: found in volume '$volume' -> $it")
                return it
            }
        }
        return null
    }

    private fun queryLrcContentUriInVolume(path: String, volume: String): Uri? {
        val collection = MediaStore.Files.getContentUri(volume)
        return try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Files.FileColumns._ID),
                "${MediaStore.Files.FileColumns.DATA} = ?",
                arrayOf(path),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(collection, cursor.getLong(0))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.d(LYRICS_TAG, "queryLrcContentUriInVolume($volume): threw ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun readTextFromUri(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
    } catch (e: Exception) {
        Log.d(LYRICS_TAG, "readTextFromUri: threw ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    /**
     * Searches a user-granted SAF folder tree (see [loadLyricsTreeUri]) for a file with the same
     * name as [lrcPath]'s. Depth-capped rather than an unbounded walk -- the granted tree is
     * expected to be a music folder, not the whole filesystem, but a cap keeps a pathological
     * grant (e.g. the SD card root) from wandering forever.
     */
    private fun readLrcViaSafTree(lrcPath: String): String? {
        val treeUriString = prefs.getString(KEY_LYRICS_TREE_URI, null)
        if (treeUriString == null) {
            Log.d(LYRICS_TAG, "readLrcViaSafTree: no folder granted yet")
            return null
        }
        val root = try {
            DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
        } catch (e: Exception) {
            Log.d(LYRICS_TAG, "readLrcViaSafTree: fromTreeUri threw ${e.javaClass.simpleName}: ${e.message}")
            null
        }
        if (root == null || !root.isDirectory) {
            Log.d(LYRICS_TAG, "readLrcViaSafTree: granted tree is missing or not a directory")
            return null
        }

        val targetName = File(lrcPath).name
        val found = findFileByName(root, targetName, maxDepth = 8)
        Log.d(LYRICS_TAG, "readLrcViaSafTree: search for '$targetName' -> ${found?.uri}")
        return found?.uri?.let { readTextFromUri(it) }
    }

    private fun findFileByName(dir: DocumentFile, name: String, maxDepth: Int): DocumentFile? {
        if (maxDepth < 0) return null
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            return null
        }
        children.firstOrNull { !it.isDirectory && it.name == name }?.let { return it }
        for (child in children) {
            if (child.isDirectory) {
                findFileByName(child, name, maxDepth - 1)?.let { return it }
            }
        }
        return null
    }

    /** Null when the user has never granted a folder for lyrics access. */
    suspend fun loadLyricsTreeUri(): Uri? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_LYRICS_TREE_URI, null)?.let { Uri.parse(it) }
    }

    suspend fun saveLyricsTreeUri(uri: Uri) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_LYRICS_TREE_URI, uri.toString()).apply()
    }

    /**
     * Writes [fields] into the track's actual file, replacing its existing tags for the fields
     * this editor supports. Requires the caller to already hold (or have just been granted) write
     * access to [AudioTrack.contentUri] -- this makes no permission requests itself, it only
     * surfaces [TagSaveResult.NeedsPermission] with the recoverable intent Android hands back
     * when it doesn't have that access, so the caller (which owns the Activity needed to launch
     * that intent) can ask and retry.
     */
    suspend fun saveTrackMetadata(track: AudioTrack, fields: MetadataFields): TagSaveResult = withContext(Dispatchers.IO) {
        val uri = track.contentUri
            ?: return@withContext TagSaveResult.Failure("${track.title}: no file location")
        if (track.formatBadge in AudioTagWriter.unsupportedFormats) {
            return@withContext TagSaveResult.Failure("${track.title}: editing ${track.formatBadge} tags isn't supported")
        }
        try {
            val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext TagSaveResult.Failure("${track.title}: couldn't read file")
            val rewritten = AudioTagWriter.rewrite(original, track.formatBadge, fields)
            // "rwt" truncates existing content before writing -- this is always a full
            // replacement, never an in-place patch, so there's nothing stale left over.
            context.contentResolver.openFileDescriptor(uri, "rwt")?.use { pfd ->
                java.io.FileOutputStream(pfd.fileDescriptor).use { it.write(rewritten) }
            } ?: return@withContext TagSaveResult.Failure("${track.title}: couldn't open file for writing")
            // The rewrite above only touches the raw bytes -- MediaStore's cached row (title,
            // artist, album, etc.) and other apps' views of the file stay stale until something
            // tells the scanner to re-index it, so force that now rather than waiting on a full
            // library rescan. Must be awaited: scanFile is async, and the caller's follow-up
            // loadTracks(force = true) would otherwise race it and re-query MediaStore before the
            // scan actually lands, reloading the same stale row it was meant to fix.
            resolveFilePath(uri)?.let { path ->
                suspendCancellableCoroutine<Unit> { continuation ->
                    MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ ->
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
            TagSaveResult.Success
        } catch (e: android.app.RecoverableSecurityException) {
            TagSaveResult.NeedsPermission(e.userAction.actionIntent.intentSender)
        } catch (e: UnsupportedOperationException) {
            TagSaveResult.Failure("${track.title}: ${e.message}")
        } catch (e: Exception) {
            e.printStackTrace()
            TagSaveResult.Failure("${track.title}: ${e.message ?: "failed to save"}")
        }
    }

    /**
     * Deletes the track's own file from MediaStore. Just attempts the delete and surfaces
     * [TrackDeleteResult.NeedsPermission] with the recoverable intent Android hands back when it
     * doesn't already have write access (same RecoverableSecurityException every write path in
     * this app already handles) -- unlike [saveTrackMetadata]'s batch edits, a single-track delete
     * has no need for the upfront createWriteRequest dance, since there's only ever one file to
     * ask permission for.
     */
    suspend fun deleteTrack(track: AudioTrack): TrackDeleteResult = withContext(Dispatchers.IO) {
        val uri = track.contentUri
            ?: return@withContext TrackDeleteResult.Failure("${track.title}: no file location")
        try {
            context.contentResolver.delete(uri, null, null)
            TrackDeleteResult.Success
        } catch (e: android.app.RecoverableSecurityException) {
            TrackDeleteResult.NeedsPermission(e.userAction.actionIntent.intentSender)
        } catch (e: Exception) {
            TrackDeleteResult.Failure(e.message ?: "failed to delete")
        }
    }

    /**
     * Derives a stable 64-bit id for an externally opened file from its URI.
     * Uses FNV-1a over the full URI string (far larger id space than Uri.hashCode's
     * 32 bits) and steers clear of the reserved -101..-103 range used by demo tracks.
     */
    private fun stableIdFor(uri: Uri): Long {
        var hash = -3750763034362895579L // FNV-1a 64-bit offset basis
        for (b in uri.toString().toByteArray(Charsets.UTF_8)) {
            hash = hash xor (b.toLong() and 0xFF)
            hash *= 1099511628211L // FNV-1a 64-bit prime
        }
        return if (hash in -103L..-101L) hash - 1000L else hash
    }

    private fun getDisplayName(uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name ?: uri.lastPathSegment
    }

    private fun getFileSize(uri: Uri): Long? {
        var size: Long? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    // Touching SharedPreferences for the first time reads and parses its XML off disk, so every
    // accessor below is suspending and hops to Dispatchers.IO -- including the writes, whose JSON
    // serialization would otherwise run on whichever thread called them (in practice, the main one).
    private val prefs by lazy {
        context.getSharedPreferences("vaporwave_prefs", Context.MODE_PRIVATE)
    }

    suspend fun hasSeenSwipeHint(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_SEEN_SWIPE_HINT, false)
    }

    suspend fun setSeenSwipeHint() = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_SEEN_SWIPE_HINT, true).apply()
    }

    suspend fun loadUseVaporwaveTheme(): Boolean = withContext(Dispatchers.IO) {
        useVaporwaveThemeSync()
    }

    suspend fun saveUseVaporwaveTheme(useVaporwave: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_USE_VAPORWAVE_THEME, useVaporwave).apply()
    }

    /**
     * Synchronous, non-suspend read -- same reasoning as skipSplashScreenSync below: the theme
     * this app launches in has to be decided at MusicViewModel's own construction (seeding its
     * StateFlow's initial value), which happens before setContent's first composition and well
     * before the ViewModel's usual async settings load would otherwise resolve it. Without this,
     * that first composition briefly rendered the StateFlow's hardcoded compile-time default
     * (Neon on, dark) regardless of what the user actually had saved, showing as a flash of the
     * wrong theme for a frame or two right at launch.
     */
    fun useVaporwaveThemeSync(): Boolean = prefs.getBoolean(KEY_USE_VAPORWAVE_THEME, false)

    // Read-only now -- kept so loadThemeMode()/themeModeSync() can migrate a pre-ThemeMode
    // install's prior dark/light choice instead of everyone landing on SYSTEM the first time
    // this version runs.
    suspend fun loadUseDarkTheme(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_USE_DARK_THEME, true)
    }

    suspend fun loadThemeMode(): ThemeMode = withContext(Dispatchers.IO) {
        themeModeSync()
    }

    /**
     * Synchronous, non-suspend read -- see useVaporwaveThemeSync's doc above for why; same
     * launch-flash reasoning applies here too, and to the same degree (dark/light and Neon
     * on/off are both decided together for that very first frame).
     *
     * Always resolves to a concrete mode, migrating an older pre-ThemeMode install's saved
     * dark/light boolean if that's all that's there -- but a genuinely fresh install (where
     * KEY_USE_DARK_THEME was never written either, not just defaulted) lands on SYSTEM rather
     * than silently inheriting that boolean's own default of "dark."
     */
    fun themeModeSync(): ThemeMode {
        prefs.getString(KEY_THEME_MODE, null)?.let { raw ->
            try {
                return ThemeMode.valueOf(raw)
            } catch (e: IllegalArgumentException) {
                // Fall through to the fresh-install/migration logic below.
            }
        }
        return if (prefs.contains(KEY_USE_DARK_THEME)) {
            if (prefs.getBoolean(KEY_USE_DARK_THEME, true)) ThemeMode.DARK else ThemeMode.LIGHT
        } else {
            ThemeMode.SYSTEM
        }
    }

    suspend fun saveThemeMode(mode: ThemeMode) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    suspend fun loadUseMaterialYou(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_USE_MATERIAL_YOU, false)
    }

    suspend fun saveUseMaterialYou(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_USE_MATERIAL_YOU, enabled).apply()
    }

    suspend fun loadUseOledBlack(): Boolean = withContext(Dispatchers.IO) {
        useOledBlackSync()
    }

    suspend fun saveUseOledBlack(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_USE_OLED_BLACK, enabled).apply()
    }

    /** Synchronous, non-suspend read -- see useVaporwaveThemeSync's doc for why (used by
     *  MainActivity to compute the launch window background before Compose runs). */
    fun useOledBlackSync(): Boolean = prefs.getBoolean(KEY_USE_OLED_BLACK, false)

    suspend fun loadSkipSplashScreen(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_SKIP_SPLASH_SCREEN, false)
    }

    suspend fun saveSkipSplashScreen(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_SKIP_SPLASH_SCREEN, enabled).apply()
    }

    /**
     * Synchronous, non-suspend read -- MainActivity needs this decided before its very first
     * composition (to seed showSplash's initial value), which is earlier than the ViewModel's
     * own async settings load would otherwise resolve it. SharedPreferences is safe to read
     * synchronously like this (already memory-cached by the time this runs).
     */
    fun skipSplashScreenSync(): Boolean = prefs.getBoolean(KEY_SKIP_SPLASH_SCREEN, false)

    suspend fun loadFavouriteIds(): Set<Long> = withContext(Dispatchers.IO) {
        val raw = prefs.getStringSet(KEY_FAVOURITE_IDS, emptySet()) ?: emptySet()
        raw.mapNotNullTo(HashSet()) { it.toLongOrNull() }
    }

    suspend fun saveFavouriteIds(ids: Set<Long>) = withContext(Dispatchers.IO) {
        prefs.edit().putStringSet(KEY_FAVOURITE_IDS, ids.mapTo(HashSet()) { it.toString() }).apply()
    }

    suspend fun loadPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return@withContext emptyList()
        try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val idsArray = obj.getJSONArray("trackIds")
                val trackIds = (0 until idsArray.length()).map { idsArray.getLong(it) }
                // optLong defaults to -1 rather than 0 (a value that could collide with a real
                // track id) when reading a playlist saved before this field existed.
                val artworkTrackId = obj.optLong("artworkTrackId", -1L).takeIf { it != -1L }
                Playlist(id = obj.getLong("id"), name = obj.getString("name"), trackIds = trackIds, artworkTrackId = artworkTrackId)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun savePlaylists(playlists: List<Playlist>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            val idsArray = JSONArray()
            playlist.trackIds.forEach { idsArray.put(it) }
            obj.put("trackIds", idsArray)
            if (playlist.artworkTrackId != null) {
                obj.put("artworkTrackId", playlist.artworkTrackId)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    suspend fun loadPlayStats(): Map<Long, PlayStat> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_PLAY_STATS, null) ?: return@withContext emptyMap()
        try {
            val array = JSONArray(raw)
            (0 until array.length()).associate { i ->
                val obj = array.getJSONObject(i)
                val id = obj.getLong("id")
                id to PlayStat(trackId = id, playCount = obj.getInt("count"), lastPlayedAt = obj.getLong("last"))
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun savePlayStats(stats: Map<Long, PlayStat>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        stats.values.forEach { stat ->
            val obj = JSONObject()
            obj.put("id", stat.trackId)
            obj.put("count", stat.playCount)
            obj.put("last", stat.lastPlayedAt)
            array.put(obj)
        }
        prefs.edit().putString(KEY_PLAY_STATS, array.toString()).apply()
    }

    suspend fun loadRecentlyOpenedFiles(): List<RecentAudioEntry> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_RECENT_OPENED_FILES, null) ?: return@withContext emptyList()
        try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                RecentAudioEntry(
                    uri = Uri.parse(obj.getString("uri")),
                    title = obj.getString("title"),
                    openedAtMs = obj.getLong("openedAt"),
                    artworkUri = obj.optString("artworkUri", "").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) },
                    sizeBytes = obj.optLong("sizeBytes", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveRecentlyOpenedFiles(entries: List<RecentAudioEntry>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("uri", entry.uri.toString())
            obj.put("title", entry.title)
            obj.put("openedAt", entry.openedAtMs)
            obj.put("sizeBytes", entry.sizeBytes)
            if (entry.artworkUri != null) {
                obj.put("artworkUri", entry.artworkUri.toString())
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_RECENT_OPENED_FILES, array.toString()).apply()
    }

    suspend fun loadEqEnabled(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_EQ_ENABLED, false)
    }

    suspend fun saveEqEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
    }

    /** Null when nothing has been saved yet -- the caller falls back to a flat (all-zero) curve. */
    suspend fun loadEqBandGains(): List<Float>? = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_EQ_BAND_GAINS, null) ?: return@withContext null
        try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getDouble(it).toFloat() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveEqBandGains(gains: List<Float>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        gains.forEach { array.put(it.toDouble()) }
        prefs.edit().putString(KEY_EQ_BAND_GAINS, array.toString()).apply()
    }

    companion object {
        private const val KEY_FAVOURITE_IDS = "favourite_track_ids"
        private const val KEY_PLAYLISTS = "playlists_json"
        private const val KEY_SEEN_SWIPE_HINT = "seen_swipe_hint"
        private const val KEY_PLAY_STATS = "play_stats_json"
        private const val KEY_RECENT_OPENED_FILES = "recent_opened_files_json"
        private const val KEY_USE_VAPORWAVE_THEME = "use_vaporwave_theme"
        private const val KEY_USE_DARK_THEME = "use_dark_theme"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_USE_MATERIAL_YOU = "use_material_you"
        private const val KEY_USE_OLED_BLACK = "use_oled_black"
        private const val KEY_SKIP_SPLASH_SCREEN = "skip_splash_screen"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_BAND_GAINS = "eq_band_gains_json"
        private const val KEY_LYRICS_TREE_URI = "lyrics_tree_uri"

        // Temporary, verbose on purpose -- diagnosing why the .lrc lookup fails on some
        // devices/OEM skins (e.g. Samsung One UI on Android 10) while working on others.
        private const val LYRICS_TAG = "VaporwaveLyrics"

        private const val EMBEDDED_LYRICS_PREFIX_BYTES = 8 * 1024 * 1024
    }
}

sealed class TrackDeleteResult {
    object Success : TrackDeleteResult()
    data class NeedsPermission(val intentSender: android.content.IntentSender) : TrackDeleteResult()
    data class Failure(val message: String) : TrackDeleteResult()
}

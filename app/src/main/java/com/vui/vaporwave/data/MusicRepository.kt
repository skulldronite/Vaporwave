package com.vui.vaporwave.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.vui.vaporwave.model.AudioTrack
import com.vui.vaporwave.model.PlayStat
import com.vui.vaporwave.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
            MediaStore.Audio.Media.DATE_ADDED
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
                            dateAddedMs = dateAddedMs
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
        var resolvedOk = false

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

        AudioTrack(
            id = stableIdFor(uri),
            title = title,
            artist = artist ?: "External File",
            album = album ?: "Local Storage",
            durationMs = durationMs,
            contentUri = uri,
            artworkUri = null,
            mimeType = mimeType ?: context.contentResolver.getType(uri) ?: "audio/*",
            sampleRateHz = sampleRate,
            bitrateKbps = bitrate,
            isDemoTrack = false
        )
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
                Playlist(id = obj.getLong("id"), name = obj.getString("name"), trackIds = trackIds)
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

    companion object {
        private const val KEY_FAVOURITE_IDS = "favourite_track_ids"
        private const val KEY_PLAYLISTS = "playlists_json"
        private const val KEY_SEEN_SWIPE_HINT = "seen_swipe_hint"
        private const val KEY_PLAY_STATS = "play_stats_json"
    }
}

package com.vui.vaporwave.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Media3's default [BitmapLoader] (used by [androidx.media3.session.MediaSession] to build the
 * system notification's artwork) is built around HTTP(S) artwork URLs and doesn't know how to
 * pull a thumbnail out of a MediaStore audio item -- which is what every [MediaMetadata]
 * artworkUri in this app actually points at, since it's a local-library player. This uses
 * [android.content.ContentResolver.loadThumbnail] (the API-29+ mechanism this app's
 * [com.vui.vaporwave.AudioThumbnailFetcher] routes Coil's AsyncImage through in-app, for the
 * same reason), which correctly extracts the item's associated/embedded art rather than trying
 * to decode the URI as a generic byte stream.
 *
 * Every [loadBitmap] call is a real MediaProvider IPC (and on some OEM MediaProvider
 * implementations, on-the-fly thumbnail generation) with no bound on latency. Media3 posts the
 * notification immediately when a track changes and only attaches artwork once this future
 * resolves, re-posting after the fact -- on a slow/loaded MediaProvider (observed on Vivo, not on
 * a Samsung S9) that second post can land late enough to be visibly delayed, or even get
 * discarded entirely if the session has already moved on to a different track by the time it
 * resolves. The cache below and [prewarm] exist to close that gap: callers that already know
 * which track is about to become current (a track tap, a shuffle pick, or the service's own
 * lookahead to the next queued item) can kick off the load ahead of when Media3 would otherwise
 * first ask for it, so by the time it does ask, the bitmap is often already sitting in cache.
 */
class ContentUriBitmapLoader(private val context: Context) : BitmapLoader {

    // A couple of worker threads rather than one: a prewarm request for the next track must not
    // queue up behind a still-in-flight load for the track that's merely about to be superseded.
    private val executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))

    // Access-ordered so the most recently used entries are the ones kept once the cache is full --
    // bounded rather than unbounded since a long shuffle session could otherwise touch the whole
    // library's worth of artwork over time.
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<Uri, ListenableFuture<Bitmap>>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Uri, ListenableFuture<Bitmap>>): Boolean =
                size > MAX_CACHE_ENTRIES
        }
    )

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit<Bitmap> {
            BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: throw IllegalStateException("Could not decode artwork bytes")
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        cache[uri]?.let { return it }
        val future = executor.submit<Bitmap> {
            context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
        }
        cache[uri] = future
        // A failed load must not poison the cache forever -- without this, one transient
        // MediaProvider error would keep returning that same failure for the rest of the
        // session instead of letting a later request try again.
        future.addListener({
            if (!didSucceed(future)) cache.remove(uri, future)
        }, MoreExecutors.directExecutor())
        return future
    }

    /**
     * Fire-and-forget: starts (or reuses an already in-flight/cached) [loadBitmap] for [uri]
     * purely to warm the cache, without anything waiting on the result here. A no-op for a null
     * uri (a track with no artwork at all) rather than making every call site null-check first.
     */
    fun prewarm(uri: Uri?) {
        if (uri != null) loadBitmap(uri)
    }

    private fun didSucceed(future: ListenableFuture<Bitmap>): Boolean = try {
        future.isDone && !future.isCancelled && future.get() != null
    } catch (e: Exception) {
        false
    }

    override fun supportsMimeType(mimeType: String): Boolean = true

    private companion object {
        const val MAX_CACHE_ENTRIES = 24
    }
}

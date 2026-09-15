package com.vui.vaporwave

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import com.vui.vaporwave.service.ContentUriBitmapLoader

/**
 * Without this, Coil falls back to an implicit default [ImageLoader] with whatever cache sizing
 * it decides on its own. Explicit here instead: a disk cache large enough that album art and
 * other thumbnails decoded once don't need re-decoding from MediaStore on a future cold start,
 * and a memory cache sized deliberately for same-session reuse.
 */
class VaporwaveApplication : Application(), SingletonImageLoader.Factory {

    // Shared (not one-per-consumer) so a prewarm kicked off from MusicViewModel (a track tap, a
    // shuffle pick) and the actual load VaporwavePlaybackService's MediaSession triggers for the
    // system notification hit the same cache -- otherwise the prewarm would just populate a
    // cache nothing else ever reads from.
    val notificationBitmapLoader: ContentUriBitmapLoader by lazy { ContentUriBitmapLoader(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AudioThumbnailKeyer())
                add(AudioThumbnailFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbnail_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}

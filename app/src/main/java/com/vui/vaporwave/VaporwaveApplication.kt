package com.vui.vaporwave

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache

/**
 * Without this, Coil falls back to an implicit default [ImageLoader] with whatever cache sizing
 * it decides on its own. Explicit here instead: a disk cache large enough that album art and
 * other thumbnails decoded once don't need re-decoding from MediaStore on a future cold start,
 * and a memory cache sized deliberately for same-session reuse.
 */
class VaporwaveApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
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

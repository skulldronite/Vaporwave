package com.vui.vaporwave

import android.content.ContentResolver
import android.provider.MediaStore
import coil3.Uri
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import coil3.toAndroidUri

/**
 * Coil3's default cache key for a request ignores requested size entirely, which meant a 128x128
 * list-row thumbnail and NowPlayingSheet's full-resolution hero art -- both for the exact same
 * track's artworkUri -- collided on one cache entry: whichever size loaded first (almost always
 * the list row, since it renders long before Now Playing is ever opened) won, and every later
 * request for that URI kept getting served that same bitmap regardless of what size it actually
 * asked for -- the root cause of the hero art looking blurry. Folding the requested size into the
 * key here keeps each size in its own cache slot, for both the memory and disk cache (Coil3
 * derives the disk cache key from this same key by default when none is set explicitly).
 */
class AudioThumbnailKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? {
        val androidUri = data.toAndroidUri()
        if (androidUri.scheme != ContentResolver.SCHEME_CONTENT || androidUri.authority != MediaStore.AUTHORITY) {
            return null
        }
        val width = options.size.width.pxOrElse { -1 }
        val height = options.size.height.pxOrElse { -1 }
        return "$data@${width}x$height"
    }
}

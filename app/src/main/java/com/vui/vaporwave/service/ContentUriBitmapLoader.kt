package com.vui.vaporwave.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
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
 */
class ContentUriBitmapLoader(private val context: Context) : BitmapLoader {

    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit<Bitmap> {
            BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: throw IllegalStateException("Could not decode artwork bytes")
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        executor.submit<Bitmap> {
            context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
        }

    override fun supportsMimeType(mimeType: String): Boolean = true
}

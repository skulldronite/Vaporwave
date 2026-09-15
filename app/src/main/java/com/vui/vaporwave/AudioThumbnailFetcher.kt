package com.vui.vaporwave

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size as AndroidSize
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.toAndroidUri
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import okio.Buffer
import okio.FileSystem

/**
 * Coil3 is multiplatform and dispatches fetchers against its own [coil3.Uri] type, not
 * [android.net.Uri] -- an internal AndroidUriMapper converts every incoming android.net.Uri to
 * coil3.Uri before any Fetcher.Factory is matched (confirmed by inspecting coil-core-android's
 * bytecode: coil3.fetch.ContentUriFetcher.Factory itself implements Fetcher.Factory<coil3.Uri>).
 * A factory registered as Fetcher.Factory<android.net.Uri> therefore never matches anything and
 * is silently skipped, which is why this previously had no effect: Coil kept falling through to
 * its own default ContentUriFetcher, which treats content:// as a generic byte stream and tries
 * to decode it directly -- fine for the album art URI's audio-adjacent bytes making it look like
 * *something* was being attempted, but wrong, since only ContentResolver.loadThumbnail actually
 * knows how to pull a thumbnail out of a MediaStore row.
 */
class AudioThumbnailFetcher(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val requestedSize: Size
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val albumId = uri.lastPathSegment
        // Every track's artworkUri points at its album's row unconditionally (see
        // MusicRepository) whether or not that album actually has art -- MediaProvider has no
        // cheaper way to tell in advance. Once loadThumbnail below has told us a given album has
        // none, skip straight to throwing instead of repeating the same failing MediaProvider IPC
        // on every re-scroll past that album's tracks for the rest of the process's life.
        if (albumId != null && albumId in albumsKnownToHaveNoArtwork) {
            throw FileNotFoundException("No artwork for album $albumId (cached miss)")
        }

        // Honor the caller's requested size (set via ImageRequest.Builder.size(...) at each call
        // site) instead of always decoding a fixed 512x512 bitmap -- a 52dp list row only needs a
        // 128x128 thumbnail, and decoding/caching a ~16x larger bitmap for it was starving the
        // memory cache and causing extra decodes (jank) on re-scroll.
        val width = requestedSize.width.pxOrElse { DEFAULT_THUMBNAIL_DIMENSION }
        val height = requestedSize.height.pxOrElse { DEFAULT_THUMBNAIL_DIMENSION }
        val bitmap = try {
            contentResolver.loadThumbnail(uri, AndroidSize(width, height), null)
        } catch (e: FileNotFoundException) {
            if (albumId != null) albumsKnownToHaveNoArtwork.add(albumId)
            throw e
        }

        // Re-encoded to JPEG bytes and returned as a SourceFetchResult (rather than handing back
        // the already-decoded Bitmap as an ImageFetchResult) so Coil's own disk-cache-write path
        // -- which only exists for SourceFetchResult, not ImageFetchResult -- actually persists
        // this thumbnail to the 250MB DiskCache configured in VaporwaveApplication. Previously
        // every thumbnail was decoded fresh from MediaStore on every cold start/cache eviction,
        // silently making that disk cache dead weight.
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().write(bytes),
                fileSystem = FileSystem.SYSTEM
            ),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val androidUri = data.toAndroidUri()
            if (androidUri.scheme != ContentResolver.SCHEME_CONTENT || androidUri.authority != MediaStore.AUTHORITY) {
                return null
            }
            return AudioThumbnailFetcher(options.context.contentResolver, androidUri, options.size)
        }
    }

    private companion object {
        const val DEFAULT_THUMBNAIL_DIMENSION = 512

        // Shared across every fetch for the process's lifetime -- album IDs are stable MediaStore
        // row identifiers, so a miss recorded once stays valid for the whole session.
        val albumsKnownToHaveNoArtwork: MutableSet<String> =
            Collections.newSetFromMap(ConcurrentHashMap())
    }
}

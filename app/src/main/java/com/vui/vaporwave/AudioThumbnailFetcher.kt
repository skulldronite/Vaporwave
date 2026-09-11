package com.vui.vaporwave

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.toAndroidUri

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
    private val uri: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bitmap = contentResolver.loadThumbnail(uri, Size(512, 512), null)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val androidUri = data.toAndroidUri()
            if (androidUri.scheme != ContentResolver.SCHEME_CONTENT || androidUri.authority != MediaStore.AUTHORITY) {
                return null
            }
            return AudioThumbnailFetcher(options.context.contentResolver, androidUri)
        }
    }
}

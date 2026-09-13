package com.vui.vaporwave.data.tagging

import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Rewrites a FLAC file's VORBIS_COMMENT (and, if new artwork is provided, PICTURE) metadata
 * blocks. STREAMINFO and any other metadata blocks are preserved byte-for-byte, and the audio
 * frames after the metadata block chain are copied through untouched -- FLAC's block structure is
 * fully self-delimited, so this never needs to know anything about the audio data itself.
 */
object FlacTagWriter {
    private const val MAGIC = "fLaC"
    private const val TYPE_VORBIS_COMMENT = 4
    private const val TYPE_PICTURE = 6

    fun rewrite(original: ByteArray, fields: MetadataFields): ByteArray {
        require(original.size >= 4 && String(original, 0, 4, Charsets.US_ASCII) == MAGIC) {
            "Not a FLAC file"
        }

        var offset = 4
        val keptBlocks = mutableListOf<ByteArray>() // full block bytes (header + data), excluding vorbis comment/picture
        var streamInfoBlock: ByteArray? = null

        while (offset + 4 <= original.size) {
            val headerByte = original[offset].toInt() and 0xFF
            val isLast = (headerByte and 0x80) != 0
            val type = headerByte and 0x7F
            val length = be24(original, offset + 1)
            val blockStart = offset
            val dataStart = offset + 4
            val blockEnd = dataStart + length
            if (blockEnd > original.size) throw IllegalStateException("Corrupt FLAC metadata block")

            val fullBlock = original.copyOfRange(blockStart, blockEnd)
            when (type) {
                0 -> streamInfoBlock = fullBlock // STREAMINFO, always kept, always first
                TYPE_VORBIS_COMMENT, TYPE_PICTURE -> Unit // dropped -- rebuilt fresh below
                1 -> Unit // PADDING -- dropped, no need to preserve wasted space
                else -> keptBlocks.add(fullBlock)
            }

            offset = blockEnd
            if (isLast) break
        }
        val audioStart = offset

        val streamInfo = streamInfoBlock ?: throw IllegalStateException("Missing STREAMINFO block")
        val newVorbisComment = buildVorbisCommentBlock(fields)
        val newPicture = fields.artworkJpeg?.let { buildPictureBlock(it) }

        val allBlocks = mutableListOf(clearLastFlag(streamInfo))
        allBlocks.addAll(keptBlocks.map { clearLastFlag(it) })
        allBlocks.add(if (newPicture == null) newVorbisComment else clearLastFlag(newVorbisComment))
        newPicture?.let { allBlocks.add(it) }
        // Mark the true last block.
        val lastIndex = allBlocks.size - 1
        allBlocks[lastIndex] = setLastFlag(allBlocks[lastIndex])

        val out = ByteArrayOutputStream(4 + allBlocks.sumOf { it.size } + (original.size - audioStart))
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        allBlocks.forEach { out.write(it) }
        out.write(original, audioStart, original.size - audioStart)
        return out.toByteArray()
    }

    private fun buildVorbisCommentBlock(fields: MetadataFields): ByteArray {
        val comments = mutableListOf<String>()
        fun add(key: String, value: String) {
            if (value.isNotBlank()) comments.add("$key=$value")
        }
        add("TITLE", fields.title)
        add("ARTIST", fields.artist)
        add("ALBUM", fields.album)
        add("ALBUMARTIST", fields.albumArtist)
        add("GENRE", fields.genre)
        add("DATE", fields.recordingDate)
        if (fields.trackNumber > 0) add("TRACKNUMBER", fields.trackNumber.toString())
        if (fields.discNumber > 0) add("DISCNUMBER", fields.discNumber.toString())

        val vendor = "Vaporwave".toByteArray(Charsets.UTF_8)
        val body = ByteArrayOutputStream()
        writeLe32(body, vendor.size)
        body.write(vendor)
        writeLe32(body, comments.size)
        for (comment in comments) {
            val bytes = comment.toByteArray(Charsets.UTF_8)
            writeLe32(body, bytes.size)
            body.write(bytes)
        }
        val bodyBytes = body.toByteArray()
        return blockHeader(TYPE_VORBIS_COMMENT, bodyBytes.size) + bodyBytes
    }

    private fun buildPictureBlock(jpeg: ByteArray): ByteArray? {
        // FLAC's block length field is only 3 bytes (max ~16MB) -- silently skip embedding rather
        // than produce a file we can't correctly frame if the art is somehow larger than that.
        if (jpeg.size > 0xFF_FFFF - 64) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        val width = bounds.outWidth.coerceAtLeast(0)
        val height = bounds.outHeight.coerceAtLeast(0)

        val mime = "image/jpeg".toByteArray(Charsets.US_ASCII)
        val body = ByteArrayOutputStream()
        writeBe32(body, 3) // picture type: front cover
        writeBe32(body, mime.size)
        body.write(mime)
        writeBe32(body, 0) // description length
        writeBe32(body, width)
        writeBe32(body, height)
        writeBe32(body, 24) // color depth
        writeBe32(body, 0) // colors used (non-indexed)
        writeBe32(body, jpeg.size)
        body.write(jpeg)
        val bodyBytes = body.toByteArray()
        return blockHeader(TYPE_PICTURE, bodyBytes.size) + bodyBytes
    }

    private fun blockHeader(type: Int, length: Int): ByteArray =
        byteArrayOf((type and 0x7F).toByte()) + be24Encode(length)

    private fun clearLastFlag(block: ByteArray): ByteArray {
        val copy = block.copyOf()
        copy[0] = (copy[0].toInt() and 0x7F).toByte()
        return copy
    }

    private fun setLastFlag(block: ByteArray): ByteArray {
        val copy = block.copyOf()
        copy[0] = (copy[0].toInt() or 0x80).toByte()
        return copy
    }

    private fun be24(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)

    private fun be24Encode(n: Int): ByteArray = byteArrayOf(
        ((n ushr 16) and 0xFF).toByte(),
        ((n ushr 8) and 0xFF).toByte(),
        (n and 0xFF).toByte()
    )

    private fun writeLe32(out: ByteArrayOutputStream, n: Int) {
        out.write(n and 0xFF)
        out.write((n ushr 8) and 0xFF)
        out.write((n ushr 16) and 0xFF)
        out.write((n ushr 24) and 0xFF)
    }

    private fun writeBe32(out: ByteArrayOutputStream, n: Int) {
        out.write((n ushr 24) and 0xFF)
        out.write((n ushr 16) and 0xFF)
        out.write((n ushr 8) and 0xFF)
        out.write(n and 0xFF)
    }
}

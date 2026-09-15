package com.vui.vaporwave.data.lyrics

/**
 * Reads the `moov/udta/meta/ilst/©lyr` atom from an M4A/MP4 container -- the iTunes-style
 * unsynced lyrics tag. A read-only, self-contained box walker (unlike
 * [com.vui.vaporwave.data.tagging.Mp4TagWriter], which additionally has to patch sample-table
 * offsets when it rewrites moov -- nothing here modifies the file, so none of that applies).
 * Only descends through a single top-level moov and stops at the first box of each type it finds
 * at each level, which is what every real encoder produces for these boxes.
 */
object Mp4LyricsReader {
    private const val LYRICS_BOX = "©lyr"

    fun read(bytes: ByteArray): String? {
        val moov = findBox(bytes, 0, bytes.size, "moov") ?: return null
        val udta = findBox(bytes, moov.first, moov.second, "udta") ?: return null
        val meta = findBox(bytes, udta.first, udta.second, "meta") ?: return null
        // 'meta' is a FullBox: 4 bytes of version/flags precede its children.
        val metaContentStart = (meta.first + 4).coerceAtMost(meta.second)
        val ilst = findBox(bytes, metaContentStart, meta.second, "ilst") ?: return null
        val lyr = findBox(bytes, ilst.first, ilst.second, LYRICS_BOX) ?: return null
        val data = findBox(bytes, lyr.first, lyr.second, "data") ?: return null

        // 'data' item: 4 bytes type, 4 bytes locale, then the payload.
        val payloadStart = data.first + 8
        if (payloadStart > data.second) return null
        return String(bytes, payloadStart, data.second - payloadStart, Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }

    /** (contentStart, contentEnd) of the first immediate child box of [type] in [start, end). */
    private fun findBox(bytes: ByteArray, start: Int, end: Int, type: String): Pair<Int, Int>? {
        var offset = start
        while (offset + 8 <= end) {
            var size = beInt(bytes, offset).toLong() and 0xFFFFFFFFL
            val boxType = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            var headerSize = 8
            if (size == 1L) {
                if (offset + 16 > end) return null
                size = beLong(bytes, offset + 8)
                headerSize = 16
            } else if (size == 0L) {
                size = (end - offset).toLong()
            }
            if (size < headerSize) return null
            val totalEnd = offset + size.toInt()
            if (totalEnd > end || totalEnd < offset) return null
            if (boxType == type) return (offset + headerSize) to totalEnd
            offset = totalEnd
        }
        return null
    }

    private fun beInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun beLong(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return value
    }
}

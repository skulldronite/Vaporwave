package com.vui.vaporwave.data.lyrics

/**
 * Reads the `LYRICS` (or `UNSYNCEDLYRICS`) Vorbis comment field from a FLAC file's
 * VORBIS_COMMENT metadata block. Mirrors the block-walking conventions
 * [com.vui.vaporwave.data.tagging.FlacTagWriter] already writes.
 */
object FlacLyricsReader {
    private const val MAGIC = "fLaC"
    private const val TYPE_VORBIS_COMMENT = 4

    fun read(bytes: ByteArray): String? {
        if (bytes.size < 4 || String(bytes, 0, 4, Charsets.US_ASCII) != MAGIC) return null

        var offset = 4
        while (offset + 4 <= bytes.size) {
            val headerByte = bytes[offset].toInt() and 0xFF
            val isLast = (headerByte and 0x80) != 0
            val type = headerByte and 0x7F
            val length = be24(bytes, offset + 1)
            val dataStart = offset + 4
            val dataEnd = dataStart + length
            if (dataEnd > bytes.size) return null

            if (type == TYPE_VORBIS_COMMENT) {
                return parseVorbisComment(bytes, dataStart, dataEnd)
            }
            offset = dataEnd
            if (isLast) break
        }
        return null
    }

    private fun parseVorbisComment(bytes: ByteArray, start: Int, end: Int): String? {
        var pos = start
        if (pos + 4 > end) return null
        val vendorLen = le32(bytes, pos)
        pos += 4 + vendorLen
        if (pos + 4 > end) return null
        val count = le32(bytes, pos)
        pos += 4

        repeat(count) {
            if (pos + 4 > end) return null
            val len = le32(bytes, pos)
            pos += 4
            if (len < 0 || pos + len > end) return null
            val comment = String(bytes, pos, len, Charsets.UTF_8)
            pos += len

            val eq = comment.indexOf('=')
            if (eq > 0) {
                val key = comment.substring(0, eq)
                if (key.equals("LYRICS", ignoreCase = true) || key.equals("UNSYNCEDLYRICS", ignoreCase = true)) {
                    comment.substring(eq + 1).trim().takeIf { it.isNotEmpty() }?.let { return it }
                }
            }
        }
        return null
    }

    private fun be24(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

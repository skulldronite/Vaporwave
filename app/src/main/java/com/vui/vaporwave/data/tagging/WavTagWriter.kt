package com.vui.vaporwave.data.tagging

import java.io.ByteArrayOutputStream

/**
 * Rewrites a WAV file's RIFF "LIST/INFO" chunk (title/artist/album/genre/date). Every other chunk
 * (fmt, data, and anything else) is copied through byte-for-byte and left in its original relative
 * order; the new INFO chunk is simply inserted right after the RIFF/WAVE header.
 *
 * Track/disc number and embedded artwork have no widely-supported standard slot in a plain RIFV
 * WAVE file, so they're silently left untouched here rather than written into a nonstandard chunk
 * that most other software would never read back.
 */
object WavTagWriter {

    fun rewrite(original: ByteArray, fields: MetadataFields): ByteArray {
        require(original.size >= 12 &&
            String(original, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(original, 8, 4, Charsets.US_ASCII) == "WAVE"
        ) { "Not a WAV file" }

        val keptChunks = mutableListOf<ByteArray>() // each: id(4) + size(4 LE) + data + pad
        var offset = 12
        while (offset + 8 <= original.size) {
            val id = String(original, offset, 4, Charsets.US_ASCII)
            val size = readLe32(original, offset + 4)
            val paddedSize = size + (size and 1)
            val chunkEnd = offset + 8 + paddedSize
            if (chunkEnd > original.size) break // trailing garbage/truncated chunk -- stop, keep what we have

            val isInfoList = id == "LIST" && offset + 12 <= original.size &&
                String(original, offset + 8, 4, Charsets.US_ASCII) == "INFO"
            if (!isInfoList) {
                keptChunks.add(original.copyOfRange(offset, chunkEnd))
            }
            offset = chunkEnd
        }

        val newInfoChunk = buildInfoChunk(fields)
        val allChunks = mutableListOf(newInfoChunk)
        allChunks.addAll(keptChunks)

        val contentSize = 4 + allChunks.sumOf { it.size } // "WAVE" + all chunks
        val out = ByteArrayOutputStream(8 + contentSize)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeLe32(out, contentSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        allChunks.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun buildInfoChunk(fields: MetadataFields): ByteArray {
        val subChunks = ByteArrayOutputStream()
        fun addSub(id: String, value: String) {
            if (value.isBlank()) return
            val bytes = value.toByteArray(Charsets.UTF_8) + byteArrayOf(0) // null-terminated
            subChunks.write(id.toByteArray(Charsets.US_ASCII))
            writeLe32(subChunks, bytes.size)
            subChunks.write(bytes)
            if (bytes.size and 1 == 1) subChunks.write(0) // pad to even
        }
        addSub("INAM", fields.title)
        addSub("IART", fields.artist)
        addSub("IPRD", fields.album)
        addSub("IGNR", fields.genre)
        addSub("ICRD", fields.recordingDate)

        val subBytes = subChunks.toByteArray()
        val listContentSize = 4 + subBytes.size // "INFO" + sub-chunks
        val out = ByteArrayOutputStream(8 + listContentSize)
        out.write("LIST".toByteArray(Charsets.US_ASCII))
        writeLe32(out, listContentSize)
        out.write("INFO".toByteArray(Charsets.US_ASCII))
        out.write(subBytes)
        return out.toByteArray()
    }

    private fun readLe32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeLe32(out: ByteArrayOutputStream, n: Int) {
        out.write(n and 0xFF)
        out.write((n ushr 8) and 0xFF)
        out.write((n ushr 16) and 0xFF)
        out.write((n ushr 24) and 0xFF)
    }
}

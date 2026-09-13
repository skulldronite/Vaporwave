package com.vui.vaporwave.data.tagging

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Rewrites an MP3's ID3v2.3 tag. Any existing ID3v2 header at the start of the file is replaced
 * wholesale with a freshly built one; every byte from the end of that header onward (the actual
 * MPEG audio frames) is copied through untouched, so this can never touch the audio itself.
 */
object Mp3TagWriter {

    fun rewrite(original: ByteArray, fields: MetadataFields): ByteArray {
        val audioStart = existingTagSize(original)
        val frames = ByteArrayOutputStream()

        writeTextFrame(frames, "TIT2", fields.title)
        writeTextFrame(frames, "TPE1", fields.artist)
        writeTextFrame(frames, "TALB", fields.album)
        writeTextFrame(frames, "TPE2", fields.albumArtist)
        writeTextFrame(frames, "TCON", fields.genre)
        writeTextFrame(frames, "TYER", fields.recordingDate)
        if (fields.trackNumber > 0) writeTextFrame(frames, "TRCK", fields.trackNumber.toString())
        if (fields.discNumber > 0) writeTextFrame(frames, "TPOS", fields.discNumber.toString())
        fields.artworkJpeg?.let { writeApicFrame(frames, it) }

        val frameBytes = frames.toByteArray()
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x03, 0x00, // version 2.3.0
            0x00// flags
        ) + synchsafe(frameBytes.size)

        val result = ByteArrayOutputStream(header.size + frameBytes.size + (original.size - audioStart))
        result.write(header)
        result.write(frameBytes)
        result.write(original, audioStart, original.size - audioStart)
        return result.toByteArray()
    }

    /** Size (in bytes) of any existing ID3v2 header at the start of the file, or 0 if there is none. */
    private fun existingTagSize(bytes: ByteArray): Int {
        if (bytes.size < 10) return 0
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) return 0
        val size = unsynchsafe(bytes, 6)
        return 10 + size
    }

    private fun writeTextFrame(out: ByteArrayOutputStream, id: String, value: String) {
        if (value.isBlank()) return
        val encoded = value.toByteArray(Charsets.UTF_16LE)
        // Encoding byte 0x01 = UTF-16 with BOM -- ID3v2.3 has no native UTF-8 frame encoding
        // (that arrived in 2.4), and this is the only 2.3 encoding that safely round-trips
        // non-Latin1 text (accented names, non-English genres, etc).
        val content = byteArrayOf(0x01, 0xFF.toByte(), 0xFE.toByte()) + encoded
        writeFrameHeader(out, id, content.size)
        out.write(content)
    }

    private fun writeApicFrame(out: ByteArrayOutputStream, jpeg: ByteArray) {
        val mime = "image/jpeg".toByteArray(Charsets.ISO_8859_1)
        val content = ByteArrayOutputStream()
        content.write(0x00) // text encoding: ISO-8859-1 (mime/description are ASCII-safe)
        content.write(mime)
        content.write(0x00) // mime type terminator
        content.write(0x03) // picture type: front cover
        content.write(0x00) // empty description, terminator only
        content.write(jpeg)
        val contentBytes = content.toByteArray()
        writeFrameHeader(out, "APIC", contentBytes.size)
        out.write(contentBytes)
    }

    private fun writeFrameHeader(out: ByteArrayOutputStream, id: String, size: Int) {
        out.write(id.toByteArray(Charset.forName("US-ASCII")))
        // Plain 32-bit big-endian size in v2.3 (synchsafe sizes are a v2.4-only change).
        out.write((size ushr 24) and 0xFF)
        out.write((size ushr 16) and 0xFF)
        out.write((size ushr 8) and 0xFF)
        out.write(size and 0xFF)
        out.write(0x00) // flags byte 1
        out.write(0x00) // flags byte 2
    }

    private fun synchsafe(size: Int): ByteArray = byteArrayOf(
        ((size ushr 21) and 0x7F).toByte(),
        ((size ushr 14) and 0x7F).toByte(),
        ((size ushr 7) and 0x7F).toByte(),
        (size and 0x7F).toByte()
    )

    private fun unsynchsafe(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }
}

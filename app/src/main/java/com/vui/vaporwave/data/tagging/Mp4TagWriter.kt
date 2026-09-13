package com.vui.vaporwave.data.tagging

import java.io.ByteArrayOutputStream

/**
 * Rewrites the iTunes-style metadata (moov/udta/meta/ilst) in an M4A/MP4 container.
 *
 * The tricky part isn't the tags themselves -- it's that replacing them changes the size of the
 * `moov` box, and every track's sample table (`stco`/`co64`) stores *absolute* file offsets into
 * `mdat`. If `mdat` sits after `moov` (the common case for these files), growing or shrinking moov
 * shifts every sample in the file, so every offset that pointed past the old end of moov has to be
 * adjusted by the same delta or every track would immediately desync/corrupt on playback. Offsets
 * that already pointed *before* moov (mdat-before-moov layouts) are left untouched since nothing
 * before moov moves.
 *
 * Only a conservative subset of MP4 is handled: a single top-level `moov`, standard 32-bit chunk
 * offsets or 64-bit `co64`, and non-fragmented files (fragmented/streaming-style MP4 using
 * `moof`/`trun` instead of a sample table isn't supported and is rejected rather than risked).
 */
object Mp4TagWriter {

    private data class Box(val type: String, val headerSize: Int, val contentStart: Int, val contentEnd: Int) {
        val totalStart get() = contentStart - headerSize
        val totalEnd get() = contentEnd
    }

    fun rewrite(original: ByteArray, fields: MetadataFields): ByteArray {
        val topBoxes = parseBoxes(original, 0, original.size)
        val moov = topBoxes.find { it.type == "moov" }
            ?: throw IllegalStateException("No moov atom found")
        if (topBoxes.any { it.type == "moof" }) {
            throw UnsupportedOperationException("Fragmented MP4 files aren't supported")
        }

        val moovChildren = parseBoxes(original, moov.contentStart, moov.contentEnd)
        val udta = moovChildren.find { it.type == "udta" }

        val newUdtaBytes = buildUdta(original, udta, fields)
        val oldUdtaSize = udta?.let { it.totalEnd - it.totalStart } ?: 0
        val delta = newUdtaBytes.size - oldUdtaSize

        // moov content minus the old udta box (if any), as a mutable copy we can patch offsets in.
        val moovRest = if (udta != null) {
            original.copyOfRange(moov.contentStart, udta.totalStart) +
                original.copyOfRange(udta.totalEnd, moov.contentEnd)
        } else {
            original.copyOfRange(moov.contentStart, moov.contentEnd)
        }.copyOf()

        patchChunkOffsets(moovRest, original, moov, udta, moov.totalEnd, delta)

        val newMoovContent = moovRest + newUdtaBytes
        val newMoovBox = box("moov", newMoovContent)

        val out = ByteArrayOutputStream(original.size + delta.coerceAtLeast(0))
        out.write(original, 0, moov.totalStart)
        out.write(newMoovBox)
        out.write(original, moov.totalEnd, original.size - moov.totalEnd)
        return out.toByteArray()
    }

    /**
     * Finds every stco/co64 box nested anywhere under moov (only descending through container
     * types that can actually lead to one -- trak/mdia/minf/stbl -- so an opaque box like stsd's
     * codec-specific data is never misread as a box tree) and rewrites any entry whose original
     * absolute offset fell at or after the old end of moov, i.e. anything living in the region
     * that just shifted by [delta].
     */
    private fun patchChunkOffsets(moovRest: ByteArray, original: ByteArray, moov: Box, udta: Box?, moovOriginalEnd: Int, delta: Int) {
        if (delta == 0) return
        val tables = mutableListOf<Box>()
        collectSampleTables(original, moov.contentStart, moov.contentEnd, tables)
        for (b in tables) {
            val localStart = translateToMoovRest(b.contentStart, moov, udta)
            when (b.type) {
                "stco" -> patchStco(moovRest, localStart, moovOriginalEnd, delta)
                "co64" -> patchCo64(moovRest, localStart, moovOriginalEnd, delta)
            }
        }
    }

    private fun collectSampleTables(original: ByteArray, start: Int, end: Int, out: MutableList<Box>) {
        val containerTypes = setOf("trak", "mdia", "minf", "stbl")
        for (child in parseBoxes(original, start, end)) {
            if (child.type == "stco" || child.type == "co64") {
                out.add(child)
            } else if (child.type in containerTypes) {
                collectSampleTables(original, child.contentStart, child.contentEnd, out)
            }
        }
    }

    /**
     * moovRest is original[moov.contentStart, udta.totalStart) ++ original[udta.totalEnd, moov.contentEnd)
     * (or simply original[moov.contentStart, moov.contentEnd) when there's no udta at all) -- maps
     * an absolute offset in the original file that falls inside moov's content into its position
     * in that spliced buffer. Never called for an offset inside udta itself, since udta can't
     * contain a sample table.
     */
    private fun translateToMoovRest(originalAbsoluteOffset: Int, moov: Box, udta: Box?): Int {
        if (udta == null || originalAbsoluteOffset < udta.totalStart) {
            return originalAbsoluteOffset - moov.contentStart
        }
        return originalAbsoluteOffset - udta.totalEnd + (udta.totalStart - moov.contentStart)
    }

    private fun buildUdta(original: ByteArray, udta: Box?, fields: MetadataFields): ByteArray {
        val udtaChildren = if (udta != null) parseBoxes(original, udta.contentStart, udta.contentEnd) else emptyList()
        val meta = udtaChildren.find { it.type == "meta" }

        val newMetaBytes = buildMeta(original, meta, fields)

        val otherUdtaChildren = udtaChildren.filter { it.type != "meta" }
            .map { original.copyOfRange(it.totalStart, it.totalEnd) }

        val content = ByteArrayOutputStream()
        otherUdtaChildren.forEach { content.write(it) }
        content.write(newMetaBytes)
        return box("udta", content.toByteArray())
    }

    private fun buildMeta(original: ByteArray, meta: Box?, fields: MetadataFields): ByteArray {
        // 'meta' is a FullBox: 4 bytes of version/flags precede its children.
        val metaChildren = if (meta != null) parseBoxes(original, meta.contentStart + 4, meta.contentEnd) else emptyList()
        val hdlr = metaChildren.find { it.type == "hdlr" }?.let { original.copyOfRange(it.totalStart, it.totalEnd) }
            ?: defaultHdlr()
        val ilst = metaChildren.find { it.type == "ilst" }

        val newIlstBytes = buildIlst(original, ilst, fields)
        val otherMetaChildren = metaChildren.filter { it.type != "hdlr" && it.type != "ilst" }
            .map { original.copyOfRange(it.totalStart, it.totalEnd) }

        val content = ByteArrayOutputStream()
        content.write(byteArrayOf(0, 0, 0, 0)) // version/flags
        content.write(hdlr)
        otherMetaChildren.forEach { content.write(it) }
        content.write(newIlstBytes)
        return box("meta", content.toByteArray())
    }

    private fun buildIlst(original: ByteArray, ilst: Box?, fields: MetadataFields): ByteArray {
        val existingItems = if (ilst != null) parseBoxes(original, ilst.contentStart, ilst.contentEnd) else emptyList()
        val handled = setOf("©nam", "©ART", "aART", "©alb", "©gen", "gnre", "©day", "trkn", "disk", "covr")
        val untouched = existingItems.filter { it.type !in handled }
            .map { original.copyOfRange(it.totalStart, it.totalEnd) }

        val content = ByteArrayOutputStream()
        untouched.forEach { content.write(it) }
        writeTextItem(content, "©nam", fields.title)
        writeTextItem(content, "©ART", fields.artist)
        writeTextItem(content, "aART", fields.albumArtist)
        writeTextItem(content, "©alb", fields.album)
        writeTextItem(content, "©gen", fields.genre)
        writeTextItem(content, "©day", fields.recordingDate)
        if (fields.trackNumber > 0) writeTrknItem(content, fields.trackNumber)
        if (fields.discNumber > 0) writeDiskItem(content, fields.discNumber)
        fields.artworkJpeg?.let { writeCovrItem(content, it) }

        return box("ilst", content.toByteArray())
    }

    private fun writeTextItem(out: ByteArrayOutputStream, type: String, value: String) {
        if (value.isBlank()) return
        val textBytes = value.toByteArray(Charsets.UTF_8)
        val data = ByteArrayOutputStream()
        writeBe32(data, 1) // data type: UTF-8 text
        writeBe32(data, 0) // locale
        data.write(textBytes)
        out.write(box(type, box("data", data.toByteArray())))
    }

    private fun writeTrknItem(out: ByteArrayOutputStream, trackNumber: Int) {
        val data = ByteArrayOutputStream()
        writeBe32(data, 0) // data type: reserved/binary
        writeBe32(data, 0) // locale
        data.write(byteArrayOf(0, 0, ((trackNumber ushr 8) and 0xFF).toByte(), (trackNumber and 0xFF).toByte(), 0, 0, 0, 0))
        out.write(box("trkn", box("data", data.toByteArray())))
    }

    private fun writeDiskItem(out: ByteArrayOutputStream, discNumber: Int) {
        val data = ByteArrayOutputStream()
        writeBe32(data, 0)
        writeBe32(data, 0)
        data.write(byteArrayOf(0, 0, ((discNumber ushr 8) and 0xFF).toByte(), (discNumber and 0xFF).toByte(), 0, 0))
        out.write(box("disk", box("data", data.toByteArray())))
    }

    private fun writeCovrItem(out: ByteArrayOutputStream, jpeg: ByteArray) {
        val data = ByteArrayOutputStream()
        writeBe32(data, 13) // data type: JPEG
        writeBe32(data, 0)
        data.write(jpeg)
        out.write(box("covr", box("data", data.toByteArray())))
    }

    private fun defaultHdlr(): ByteArray {
        val content = ByteArrayOutputStream()
        writeBe32(content, 0) // version/flags
        writeBe32(content, 0) // predefined
        content.write("mdir".toByteArray(Charsets.US_ASCII)) // handler type
        content.write(ByteArray(12)) // reserved
        content.write(0) // empty name (pascal/c-string terminator)
        return box("hdlr", content.toByteArray())
    }

    private fun patchStco(buffer: ByteArray, contentStart: Int, moovOriginalEnd: Int, delta: Int) {
        val entryCount = readBe32(buffer, contentStart + 4)
        var pos = contentStart + 8
        repeat(entryCount) {
            val value = readBe32(buffer, pos)
            if (value >= moovOriginalEnd) writeBe32At(buffer, pos, value + delta)
            pos += 4
        }
    }

    private fun patchCo64(buffer: ByteArray, contentStart: Int, moovOriginalEnd: Int, delta: Int) {
        val entryCount = readBe32(buffer, contentStart + 4)
        var pos = contentStart + 8
        repeat(entryCount) {
            val value = readBe64(buffer, pos)
            if (value >= moovOriginalEnd) writeBe64At(buffer, pos, value + delta)
            pos += 8
        }
    }

    // -- box parsing --------------------------------------------------------------------------

    /**
     * Flat list of immediate child boxes in [start, end). Recurses no further -- callers descend
     * into a specific known-container child themselves via another call to this function, so we
     * never risk walking into an opaque leaf (codec data, raw samples, etc.) as if it were boxes.
     */
    private fun parseBoxes(bytes: ByteArray, start: Int, end: Int): List<Box> {
        val boxes = mutableListOf<Box>()
        var offset = start
        while (offset + 8 <= end) {
            var size = readBe32(bytes, offset).toLong() and 0xFFFFFFFFL
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            var headerSize = 8
            if (size == 1L) {
                if (offset + 16 > end) break
                size = readBe64(bytes, offset + 8)
                headerSize = 16
            } else if (size == 0L) {
                size = (end - offset).toLong()
            }
            val totalEnd = offset + size.toInt()
            if (size < headerSize || totalEnd > end) break
            boxes.add(Box(type, headerSize, offset + headerSize, totalEnd))
            offset = totalEnd
        }
        return boxes
    }

    private fun box(type: String, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(8 + content.size)
        writeBe32(out, 8 + content.size)
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(content)
        return out.toByteArray()
    }

    private fun readBe32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun readBe64(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return value
    }

    private fun writeBe32(out: ByteArrayOutputStream, n: Int) {
        out.write((n ushr 24) and 0xFF)
        out.write((n ushr 16) and 0xFF)
        out.write((n ushr 8) and 0xFF)
        out.write(n and 0xFF)
    }

    private fun writeBe32At(bytes: ByteArray, offset: Int, n: Int) {
        bytes[offset] = ((n ushr 24) and 0xFF).toByte()
        bytes[offset + 1] = ((n ushr 16) and 0xFF).toByte()
        bytes[offset + 2] = ((n ushr 8) and 0xFF).toByte()
        bytes[offset + 3] = (n and 0xFF).toByte()
    }

    private fun writeBe64At(bytes: ByteArray, offset: Int, n: Long) {
        for (i in 0 until 8) {
            bytes[offset + i] = ((n ushr (8 * (7 - i))) and 0xFF).toByte()
        }
    }
}

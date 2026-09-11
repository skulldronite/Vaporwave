package com.vui.vaporwave.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AudioTrackTest {

    @Test
    fun testFormatBadgeDetection() {
        val flacTrack = AudioTrack(
            id = 1L,
            title = "Test FLAC",
            artist = "Artist",
            album = "Album",
            durationMs = 180000L,
            mimeType = "audio/flac"
        )
        assertEquals("FLAC", flacTrack.formatBadge)

        val mp3Track = AudioTrack(
            id = 2L,
            title = "Test MP3",
            artist = "Artist",
            album = "Album",
            durationMs = 180000L,
            mimeType = "audio/mpeg"
        )
        assertEquals("MP3", mp3Track.formatBadge)

        val wavTrack = AudioTrack(
            id = 3L,
            title = "Test WAV",
            artist = "Artist",
            album = "Album",
            durationMs = 180000L,
            mimeType = "audio/x-wav"
        )
        assertEquals("WAV", wavTrack.formatBadge)

        val opusTrack = AudioTrack(
            id = 4L,
            title = "Test OPUS",
            artist = "Artist",
            album = "Album",
            durationMs = 180000L,
            mimeType = "audio/opus"
        )
        assertEquals("OPUS", opusTrack.formatBadge)
    }

    @Test
    fun testDurationFormatting() {
        val track1 = AudioTrack(
            id = 1L,
            title = "Short Track",
            artist = "Artist",
            album = "Album",
            durationMs = 75000L // 1 min 15 sec
        )
        assertEquals("01:15", track1.formattedDuration)

        val track2 = AudioTrack(
            id = 2L,
            title = "Long Track",
            artist = "Artist",
            album = "Album",
            durationMs = 3665000L // 1 hour 1 min 5 sec
        )
        assertEquals("1:01:05", track2.formattedDuration)
    }

    @Test
    fun testTechnicalDetails() {
        val track = AudioTrack(
            id = 10L,
            title = "Hi-Res Master",
            artist = "Vapor Artist",
            album = "Aesthetic",
            durationMs = 200000L,
            mimeType = "audio/flac",
            sampleRateHz = 96000,
            bitDepth = 24
        )
        assertEquals("FLAC · 24-bit · 96 kHz · Lossless", track.technicalDetails)
    }
}

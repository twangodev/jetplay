package dev.twango.jetplay.transcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin

class MediaInfoExtractorTest {

    @Test
    fun extractsTechnicalMetadataFromPcmWav() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val wav = generateSineWav(durationSec = 2.0, sampleRate = 8000, freq = 440.0)
        try {
            val info = MediaInfoExtractor.extract(wav)
            assertNotNull("expected metadata for a valid WAV", info)
            info!!

            assertEquals("pcm_s16le", info.codec)
            assertEquals("wav", info.container)
            assertEquals(8000, info.sampleRateHz)
            assertEquals(1, info.channels)
            assertEquals("mono", info.channelLabel)
            // Bit depth comes from the PCM codec name, not the (widened) sample format.
            assertEquals("16-bit", info.bitDepth)

            val durationMs = info.durationMs
            assertNotNull("duration should be probed", durationMs)
            assertTrue("a 2s clip should report ~2000ms", durationMs!! in 1900..2100)

            val sizeBytes = info.sizeBytes
            assertNotNull("size must be the real file length", sizeBytes)
            assertTrue(sizeBytes!! > 0L)

            assertNotNull("PCM WAV has a derivable bitrate", info.bitrateBps)
            assertTrue("a bare generated WAV carries no tags", info.tags.isEmpty())
            assertNull("a bare generated WAV has no cover art", info.albumArt)
        } finally {
            wav.delete()
        }
    }

    @Test
    fun buildTagsOrdersLabelsAndSkipsBlanksAndUnknowns() {
        val tags = MediaInfoExtractor.buildTags(
            mapOf(
                "ARTIST" to "Daft Punk", // upper-case key still matches
                "title" to "Aerodynamic",
                "album" to "Discovery",
                "date" to "2001",
                "genre" to "   ", // blank -> skipped
                "unknown_key" to "x", // not a surfaced field -> skipped
            ),
        )
        assertEquals(listOf("Title", "Artist", "Album", "Date"), tags.map { it.label })
        assertEquals("Aerodynamic", tags.first().value)
    }

    @Test
    fun returnsNullForNonAudioInput() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val notAudio = Files.createTempFile("jetplay-not-audio", ".bin").toFile()
        notAudio.writeBytes(ByteArray(2048) { 0x7f })
        try {
            assertNull(MediaInfoExtractor.extract(notAudio))
        } finally {
            notAudio.delete()
        }
    }

    @Test
    fun extractsVideoMetadataFromWebm() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val webm = File("assets/sintel.webm")
        Assume.assumeTrue("sintel.webm fixture required", webm.exists())

        val info = MediaInfoExtractor.extract(webm)!!
        assertEquals(854, info.width)
        assertEquals(480, info.height)
        assertEquals("vp9", info.videoCodec)
        assertNotNull("frame rate should be probed", info.frameRate)
        assertNotNull("source pixel format should be probed", info.pixelFormat)
        // The webm also carries an Opus audio stream.
        assertEquals("opus", info.codec)
        assertNotNull("audio channels should be probed", info.channels)
    }

    private fun generateSineWav(durationSec: Double, sampleRate: Int, freq: Double): File {
        val sampleCount = (durationSec * sampleRate).toInt()
        val dataBytes = sampleCount * 2 // s16 mono
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(1) // mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)
        buf.putShort(2)
        buf.putShort(16)
        buf.put("data".toByteArray())
        buf.putInt(dataBytes)
        for (i in 0 until sampleCount) {
            buf.putShort((sin(2 * PI * freq * i / sampleRate) * 30000).toInt().toShort())
        }
        val file = Files.createTempFile("jetplay-mediainfo-test", ".wav").toFile()
        file.writeBytes(buf.array())
        return file
    }
}

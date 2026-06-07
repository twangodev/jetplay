package dev.twango.jetplay.transcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin

class WaveformExtractorTest {

    @Test
    fun extractsNormalizedBarsFromAudio() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val wav = generateSineWav(durationSec = 2.0, sampleRate = 8000, freq = 440.0)
        try {
            val bars = WaveformExtractor.extract(wav, barsPerSecond = 8)

            // ~2s * 8 bars/s = ~16 bars (allow slack for the trailing partial bar)
            assertTrue("expected ~16 bars, got ${bars.size}", bars.size in 13..19)
            assertTrue("every bar must be normalized to [0,1]", bars.all { it in 0.0..1.0 })
            assertTrue("a sustained sine must read as non-silent", bars.count { it > 0.1 } >= bars.size - 2)
        } finally {
            wav.delete()
        }
    }

    @Test
    fun returnsEmptyForSilentlyUnreadableInput() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val notAudio = Files.createTempFile("jetplay-not-audio", ".bin").toFile()
        notAudio.writeBytes(ByteArray(2048) { 0x7f })
        try {
            assertEquals(emptyList<Double>(), WaveformExtractor.extract(notAudio, barsPerSecond = 8))
        } finally {
            notAudio.delete()
        }
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
        val file = Files.createTempFile("jetplay-wave-test", ".wav").toFile()
        file.writeBytes(buf.array())
        return file
    }
}

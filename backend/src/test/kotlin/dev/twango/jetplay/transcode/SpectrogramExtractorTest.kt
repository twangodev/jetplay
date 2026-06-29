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
import kotlin.math.pow
import kotlin.math.sin

class SpectrogramExtractorTest {

    @Test
    fun brightestBinTracksTheToneFrequency() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val freq = 440.0
        val sampleRate = 8000
        val wav = generateSineWav(durationSec = 2.0, sampleRate = sampleRate, freq = freq)
        try {
            val spec = SpectrogramExtractor.extract(wav)
            assertNotNull("expected a spectrogram for a clean tone", spec)
            spec!!

            assertEquals(256, spec.freqBins)
            assertTrue("expected some time columns", spec.timeCols > 0)
            assertEquals(spec.timeCols * spec.freqBins, spec.magnitudes.size)
            assertEquals(sampleRate, spec.sampleRateHz)
            assertEquals(20, spec.minHz)
            assertEquals(sampleRate / 2, spec.maxHz) // Nyquist below the 20 kHz ceiling
            assertTrue("a sustained tone must not be silent", spec.magnitudes.any { (it.toInt() and 0xFF) > 64 })

            // The bin holding the most total energy should sit at the tone frequency.
            val energyPerBin = DoubleArray(spec.freqBins)
            for (col in 0 until spec.timeCols) {
                for (bin in 0 until spec.freqBins) {
                    energyPerBin[bin] += (spec.magnitudes[col * spec.freqBins + bin].toInt() and 0xFF)
                }
            }
            val peakBin = energyPerBin.indices.maxByOrNull { energyPerBin[it] }!!
            val peakHz = binCenterHz(peakBin, spec.minHz, spec.maxHz, spec.freqBins)
            assertTrue("peak at ${peakHz}Hz should be near ${freq}Hz", peakHz in (freq * 0.85)..(freq * 1.18))
        } finally {
            wav.delete()
        }
    }

    @Test
    fun returnsNullForUnreadableInput() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val notAudio = Files.createTempFile("jetplay-not-audio", ".bin").toFile()
        notAudio.writeBytes(ByteArray(2048) { 0x7f })
        try {
            assertNull(SpectrogramExtractor.extract(notAudio))
        } finally {
            notAudio.delete()
        }
    }

    @Test
    fun producesAColumnForAClipShorterThanTheFftWindow() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        // 0.05s @ 8 kHz = 400 samples, well under the 4096-sample FFT window.
        val wav = generateSineWav(durationSec = 0.05, sampleRate = 8000, freq = 440.0)
        try {
            val spec = SpectrogramExtractor.extract(wav)
            assertNotNull("a short clip should still yield a zero-padded column", spec)
            assertTrue("expected at least one column", spec!!.timeCols >= 1)
            assertEquals(spec.timeCols * spec.freqBins, spec.magnitudes.size)
        } finally {
            wav.delete()
        }
    }

    private fun binCenterHz(bin: Int, minHz: Int, maxHz: Int, freqBins: Int): Double =
        minHz * (maxHz.toDouble() / minHz).pow(bin.toDouble() / (freqBins - 1))

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
        val file = Files.createTempFile("jetplay-spectro-test", ".wav").toFile()
        file.writeBytes(buf.array())
        return file
    }
}

package dev.twango.jetplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpectrogramTest {

    // Spectrogram carries a ByteArray, so it hand-rolls equals/hashCode over contents; verify that contract holds,
    // since the transport relies on value semantics rather than array identity (same as TranscodeEvent.Chunk).
    @Test
    fun spectrogramsWithEqualContentAreEqual() {
        val a = sample(byteArrayOf(1, 2, 3, 4))
        val b = sample(byteArrayOf(1, 2, 3, 4))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun spectrogramsWithDifferentMagnitudesAreNotEqual() {
        assertNotEquals(sample(byteArrayOf(1, 2, 3, 4)), sample(byteArrayOf(1, 2, 3, 5)))
    }

    @Test
    fun spectrogramsWithDifferentHeadersAreNotEqual() {
        assertNotEquals(sample(byteArrayOf(1, 2)).copy(timeCols = 1), sample(byteArrayOf(1, 2)).copy(timeCols = 2))
    }

    private fun sample(magnitudes: ByteArray) = Spectrogram(
        timeCols = 2,
        freqBins = 2,
        durationMs = 1000,
        sampleRateHz = 44100,
        minHz = 20,
        maxHz = 20000,
        dbFloor = -80f,
        dbCeil = 0f,
        logFreq = true,
        magnitudes = magnitudes,
    )
}

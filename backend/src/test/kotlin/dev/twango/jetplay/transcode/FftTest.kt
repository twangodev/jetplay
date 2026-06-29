package dev.twango.jetplay.transcode

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class FftTest {

    @Test
    fun constantSignalConcentratesAllEnergyInDcBin() {
        val re = doubleArrayOf(1.0, 1.0, 1.0, 1.0)
        val im = DoubleArray(4)
        Fft.transform(re, im)
        assertEquals(4.0, re[0], EPS)
        assertEquals(0.0, im[0], EPS)
        for (k in 1 until 4) assertEquals("bin $k must be zero", 0.0, hypot(re[k], im[k]), EPS)
    }

    @Test
    fun unitImpulseProducesFlatSpectrum() {
        val re = doubleArrayOf(1.0, 0.0, 0.0, 0.0)
        val im = DoubleArray(4)
        Fft.transform(re, im)
        for (k in 0 until 4) assertEquals("bin $k magnitude", 1.0, hypot(re[k], im[k]), EPS)
    }

    @Test
    fun pureTonePeaksAtItsBin() {
        val n = 64
        val k0 = 5
        val re = DoubleArray(n) { cos(2 * PI * k0 * it / n) }
        val im = DoubleArray(n)
        Fft.transform(re, im)
        val peak = (0..n / 2).maxByOrNull { hypot(re[it], im[it]) }
        assertEquals(k0, peak)
    }

    @Test
    fun matchesNaiveDftForArbitrarySignal() {
        val n = 16
        val signal = DoubleArray(n) { sin(0.7 * it) + 0.3 * cos(1.9 * it) }
        val re = signal.copyOf()
        val im = DoubleArray(n)
        Fft.transform(re, im)
        for (k in 0 until n) {
            var dftRe = 0.0
            var dftIm = 0.0
            for (t in 0 until n) {
                val ang = -2 * PI * k * t / n
                dftRe += signal[t] * cos(ang)
                dftIm += signal[t] * sin(ang)
            }
            assertEquals("re[$k]", dftRe, re[k], EPS)
            assertEquals("im[$k]", dftIm, im[k], EPS)
        }
    }

    companion object {
        private const val EPS = 1e-9
    }
}

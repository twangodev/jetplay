package dev.twango.jetplay.transcode

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 Cooley-Tukey FFT; [re]/[im] length must be a power of two. */
object Fft {

    /**
     * Twiddle factors `W_N^i = exp(-2πi·i/N)` for `i in 0 until N/2`, precomputed once per size.
     * A direct table (vs rotating a running twiddle through the inner loop) keeps the error ~O(log N)
     * instead of ~O(√N) and lifts the trig out of the hot loop — it's reused across every transform.
     */
    private class Twiddles(val n: Int) {
        val cosTable = DoubleArray(n / 2)
        val sinTable = DoubleArray(n / 2)

        init {
            for (i in 0 until n / 2) {
                val angle = -2.0 * PI * i / n
                cosTable[i] = cos(angle)
                sinTable[i] = sin(angle)
            }
        }
    }

    // Benign race: concurrent callers with the same size may each build a table, but the results are
    // identical and immutable, and the volatile publish makes a fully-built table visible.
    @Volatile
    private var cached: Twiddles? = null

    private fun twiddles(n: Int): Twiddles {
        val current = cached
        if (current != null && current.n == n) return current
        return Twiddles(n).also { cached = it }
    }

    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n == im.size) { "re/im length mismatch" }
        require(n > 0 && (n and (n - 1)) == 0) { "length must be a power of two, was $n" }
        if (n == 1) return

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }

        // Butterflies. At stage length `len`, twiddle k is W_len^k = table[k · n/len].
        val tw = twiddles(n)
        val cosTable = tw.cosTable
        val sinTable = tw.sinTable
        var len = 2
        while (len <= n) {
            val half = len / 2
            val stride = n / len
            var start = 0
            while (start < n) {
                var idx = 0
                for (k in 0 until half) {
                    val a = start + k
                    val b = a + half
                    val wRe = cosTable[idx]
                    val wIm = sinTable[idx]
                    val tRe = re[b] * wRe - im[b] * wIm
                    val tIm = re[b] * wIm + im[b] * wRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    idx += stride
                }
                start += len
            }
            len = len shl 1
        }
    }
}

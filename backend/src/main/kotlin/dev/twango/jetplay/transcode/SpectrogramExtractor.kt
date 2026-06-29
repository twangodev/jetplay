package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import dev.twango.jetplay.media.Spectrogram
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Decodes audio into a log-frequency STFT magnitude heatmap; null if there is no usable audio. */
object SpectrogramExtractor {

    private val log = Logger.getInstance(SpectrogramExtractor::class.java)

    // Large window for fine low-frequency resolution (~12 Hz/bin at 48 kHz); the log bins below a few
    // hundred Hz starve on a small FFT and read blocky. Hop is decoupled (75% overlap) to keep time detail.
    private const val FFT_SIZE = 4096
    private const val HOP = 1024
    private const val FREQ_BINS = 256
    private const val MIN_HZ = 20
    private const val MAX_HZ_CEILING = 20_000
    private const val MAX_DURATION_SECONDS = 30 * 60
    private const val TARGET_COLS = 2000
    private const val SHORT_FULL_SCALE = 32768.0
    private const val MONO_DOWNMIX = 1
    private const val MICROS_PER_SECOND = 1_000_000.0
    private const val MILLIS_PER_SECOND = 1000L
    private const val DB_FLOOR = -80f
    private const val DB_CEIL = 0f
    private const val SILENCE_FLOOR = 1e-10
    private const val MAX_BYTE = 255

    // Full-scale tone reads ~0 dBFS: FFT peak ≈ amp * FFT_SIZE * hannCoherentGain(0.5) / 2.
    private const val REF_MAGNITUDE = FFT_SIZE / 4.0

    fun extract(file: File): Spectrogram? {
        val grabber = FFmpegFrameGrabber(file).apply {
            audioChannels = MONO_DOWNMIX
            sampleMode = FrameGrabber.SampleMode.SHORT
        }
        return try {
            grabber.start()
            val sampleRate = grabber.sampleRate
            val maxHz = min(sampleRate / 2, MAX_HZ_CEILING)
            // lengthInTime is unreliable, so the frame loop also enforces the real cap.
            val durationSeconds = grabber.lengthInTime / MICROS_PER_SECOND
            when {
                sampleRate <= 0 || maxHz <= MIN_HZ -> null

                durationSeconds > MAX_DURATION_SECONDS -> {
                    log.info("Skipping spectrogram for ${file.name}: ${durationSeconds.roundToInt()}s exceeds cap")
                    null
                }

                else -> sampleToSpectrogram(grabber, sampleRate, maxHz)
            }
        } catch (e: Exception) {
            log.warn("Spectrogram extraction failed for ${file.name}", e)
            null
        } finally {
            safely("grabber.stop") { grabber.stop() }
            safely("grabber.release") { grabber.release() }
        }
    }

    private fun sampleToSpectrogram(grabber: FFmpegFrameGrabber, sampleRate: Int, maxHz: Int): Spectrogram? {
        val hann = DoubleArray(FFT_SIZE) { 0.5 * (1 - cos(2 * Math.PI * it / (FFT_SIZE - 1))) }
        val (loIdx, hiIdx) = logBinRanges(sampleRate, maxHz)

        val window = DoubleArray(FFT_SIZE)
        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)
        val fftMag = DoubleArray(FFT_SIZE / 2 + 1)
        val column = FloatArray(FREQ_BINS)
        val pool = TimePool(TARGET_COLS, FREQ_BINS)

        // Bounds the decode even when container duration is unknown.
        val maxFrames = MAX_DURATION_SECONDS.toLong() * sampleRate / HOP + 1
        var frames = 0L
        var totalSamples = 0L
        var filled = 0
        var hitDurationCap = false

        var done = false
        while (!done && !Thread.currentThread().isInterrupted) {
            val frame = grabber.grabSamples()
            if (frame == null) {
                done = true
            } else {
                val buffer = frame.samples?.firstOrNull() as? ShortBuffer
                while (buffer != null && buffer.hasRemaining() && !done) {
                    window[filled++] = buffer.get() / SHORT_FULL_SCALE
                    totalSamples++
                    if (filled == FFT_SIZE) {
                        computeColumn(window, hann, re, im, fftMag, loIdx, hiIdx, column)
                        pool.add(column)
                        frames++
                        if (frames >= maxFrames) {
                            hitDurationCap = true
                            done = true
                        }
                        // Slide the window by one hop, retaining the overlap; harmless on the final frame.
                        System.arraycopy(window, HOP, window, 0, FFT_SIZE - HOP)
                        filled = FFT_SIZE - HOP
                    }
                }
            }
        }

        // Unreliable container duration but actually over the cap: honor the null contract rather than clip.
        if (hitDurationCap) return null

        // Flush a final zero-padded frame so short clips and the trailing partial hop still yield a column.
        val pendingSamples = if (frames == 0L) filled else filled - (FFT_SIZE - HOP)
        if (pendingSamples > 0) {
            for (i in filled until FFT_SIZE) window[i] = 0.0
            computeColumn(window, hann, re, im, fftMag, loIdx, hiIdx, column)
            pool.add(column)
        }

        val cols = pool.columns()
        if (cols.isEmpty()) return null
        val magnitudes = quantize(cols)
        val durationMs = totalSamples * MILLIS_PER_SECOND / sampleRate
        return Spectrogram(
            timeCols = cols.size,
            freqBins = FREQ_BINS,
            durationMs = durationMs,
            sampleRateHz = sampleRate,
            minHz = MIN_HZ,
            maxHz = maxHz,
            dbFloor = DB_FLOOR,
            dbCeil = DB_CEIL,
            logFreq = true,
            magnitudes = magnitudes,
        )
    }

    /** FFT bin index range feeding each log-spaced output bin (max-aggregated). */
    private fun logBinRanges(sampleRate: Int, maxHz: Int): Pair<IntArray, IntArray> {
        val ratio = (maxHz.toDouble() / MIN_HZ).pow(1.0 / (FREQ_BINS - 1))
        val halfStep = sqrt(ratio)
        val nyquistBin = FFT_SIZE / 2
        val lo = IntArray(FREQ_BINS)
        val hi = IntArray(FREQ_BINS)
        for (b in 0 until FREQ_BINS) {
            val center = MIN_HZ * ratio.pow(b.toDouble())
            var l = (center / halfStep * FFT_SIZE / sampleRate).roundToInt().coerceIn(0, nyquistBin)
            var h = (center * halfStep * FFT_SIZE / sampleRate).roundToInt().coerceIn(0, nyquistBin)
            if (h < l) h = l
            lo[b] = l
            hi[b] = h
        }
        return lo to hi
    }

    private fun computeColumn(
        window: DoubleArray,
        hann: DoubleArray,
        re: DoubleArray,
        im: DoubleArray,
        fftMag: DoubleArray,
        loIdx: IntArray,
        hiIdx: IntArray,
        out: FloatArray,
    ) {
        for (i in 0 until FFT_SIZE) {
            re[i] = window[i] * hann[i]
            im[i] = 0.0
        }
        Fft.transform(re, im)
        for (k in fftMag.indices) fftMag[k] = hypot(re[k], im[k])
        for (b in out.indices) {
            var m = 0.0
            for (k in loIdx[b]..hiIdx[b]) if (fftMag[k] > m) m = fftMag[k]
            val db = 20.0 * log10(max(m / REF_MAGNITUDE, SILENCE_FLOOR))
            out[b] = db.coerceIn(DB_FLOOR.toDouble(), DB_CEIL.toDouble()).toFloat()
        }
    }

    private fun quantize(cols: List<FloatArray>): ByteArray {
        val span = DB_CEIL - DB_FLOOR
        val out = ByteArray(cols.size * FREQ_BINS)
        for (c in cols.indices) {
            val src = cols[c]
            val base = c * FREQ_BINS
            for (b in 0 until FREQ_BINS) {
                out[base + b] = ((src[b] - DB_FLOOR) / span * MAX_BYTE).roundToInt().coerceIn(0, MAX_BYTE).toByte()
            }
        }
        return out
    }

    /** Online max-pool that keeps memory flat by halving column count whenever it reaches `2 * targetCols`. */
    private class TimePool(private val targetCols: Int, private val bins: Int) {
        private var cols = ArrayList<FloatArray>()
        private var poolFactor = 1
        private var frameIdx = 0

        fun add(column: FloatArray) {
            var target = frameIdx / poolFactor
            if (target == cols.size && cols.size == 2 * targetCols) {
                halve()
                target = frameIdx / poolFactor
            }
            if (target < cols.size) {
                val dst = cols[target]
                for (i in 0 until bins) if (column[i] > dst[i]) dst[i] = column[i]
            } else {
                cols.add(column.copyOf())
            }
            frameIdx++
        }

        private fun halve() {
            val merged = ArrayList<FloatArray>(targetCols + 1)
            var i = 0
            while (i < cols.size) {
                val a = cols[i]
                if (i + 1 < cols.size) {
                    val b = cols[i + 1]
                    for (k in 0 until bins) if (b[k] > a[k]) a[k] = b[k]
                }
                merged.add(a)
                i += 2
            }
            cols = merged
            poolFactor *= 2
        }

        fun columns(): List<FloatArray> = cols
    }

    private inline fun safely(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn("$action failed", e)
        }
    }
}

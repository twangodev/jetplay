package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import java.nio.ShortBuffer
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Decodes audio into normalized amplitude bars; output matches the UI's `sampleWaveform`. */
object WaveformExtractor {

    private val log = Logger.getInstance(WaveformExtractor::class.java)

    private const val DEFAULT_BARS_PER_SECOND = 8
    private const val MAX_DURATION_SECONDS = 30 * 60
    private const val GAIN = 3.0
    private const val SHORT_FULL_SCALE = 32768.0
    private const val QUANTIZE = 100.0
    private const val MONO_DOWNMIX = 1
    private const val MICROS_PER_SECOND = 1_000_000.0
    private const val INITIAL_BARS_CAPACITY = 4096

    /** One normalized amplitude per `1/[barsPerSecond]` of audio; empty if [file] has no audio or exceeds the cap. */
    fun extract(file: File, barsPerSecond: Int = DEFAULT_BARS_PER_SECOND): List<Double> {
        val grabber = FFmpegFrameGrabber(file).apply {
            audioChannels = MONO_DOWNMIX
            sampleMode = FrameGrabber.SampleMode.SHORT
        }
        return try {
            grabber.start()
            // lengthInTime is unreliable, so sampleToBars enforces the real cap.
            val durationSeconds = grabber.lengthInTime / MICROS_PER_SECOND
            if (durationSeconds > MAX_DURATION_SECONDS) {
                log.info("Skipping waveform for ${file.name}: ${durationSeconds.roundToInt()}s exceeds cap")
                return emptyList()
            }
            sampleToBars(grabber, barsPerSecond)
        } catch (e: Exception) {
            log.warn("Waveform extraction failed for ${file.name}", e)
            emptyList()
        } finally {
            safely("grabber.stop") { grabber.stop() }
            safely("grabber.release") { grabber.release() }
        }
    }

    private fun sampleToBars(grabber: FFmpegFrameGrabber, barsPerSecond: Int): List<Double> {
        val samplesPerBar = (grabber.sampleRate / barsPerSecond).coerceAtLeast(1)
        // Bounds the decode even when container duration is unknown.
        val maxBars = MAX_DURATION_SECONDS * barsPerSecond
        val bars = ArrayList<Double>(minOf(maxBars, INITIAL_BARS_CAPACITY))
        var sum = 0.0
        var count = 0
        while (bars.size < maxBars && !Thread.currentThread().isInterrupted) {
            val frame = grabber.grabSamples() ?: break
            val buffer = frame.samples?.firstOrNull() as? ShortBuffer
            if (buffer != null) {
                while (buffer.hasRemaining() && bars.size < maxBars) {
                    sum += abs(buffer.get().toInt()) / SHORT_FULL_SCALE
                    count++
                    if (count == samplesPerBar) {
                        bars.add(normalize(sum / count))
                        sum = 0.0
                        count = 0
                    }
                }
            }
        }
        if (count > 0 && bars.size < maxBars) bars.add(normalize(sum / count))
        return bars
    }

    private fun normalize(average: Double): Double = (min(1.0, average * GAIN) * QUANTIZE).roundToInt() / QUANTIZE

    private inline fun safely(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn("$action failed", e)
        }
    }
}

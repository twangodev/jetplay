package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import java.nio.ShortBuffer
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes an audio file into normalized amplitude bars for the UI waveform.
 *
 * Computed here with the bundled FFmpeg and pushed to the page — cheaper than
 * the browser fetching and decoding the whole file. The output matches the
 * shape the UI's `sampleWaveform` produces: amplitudes in `[0, 1]` at a fixed
 * bars-per-second.
 */
object WaveformExtractor {

    private val log = Logger.getInstance(WaveformExtractor::class.java)

    private const val DEFAULT_BARS_PER_SECOND = 8
    private const val MAX_DURATION_SECONDS = 30 * 60
    private const val GAIN = 3.0
    private const val SHORT_FULL_SCALE = 32768.0
    private const val QUANTIZE = 100.0

    /**
     * Returns one normalized amplitude per `1/[barsPerSecond]` of audio, or an
     * empty list if [file] has no readable audio or exceeds the duration cap.
     */
    fun extract(file: File, barsPerSecond: Int = DEFAULT_BARS_PER_SECOND): List<Double> {
        val grabber = FFmpegFrameGrabber(file).apply {
            audioChannels = 1 // request a mono downmix
            sampleMode = FrameGrabber.SampleMode.SHORT
        }
        return try {
            grabber.start()
            // No explicit no-audio guard: grabSamples() yields nothing for a
            // file without an audio stream, so sampleToBars returns empty.
            // Fast path: skip when the container reports a length over the cap.
            // (lengthInTime is 0 / AV_NOPTS_VALUE for some inputs, so this is a
            // best-effort skip; sampleToBars enforces the real ceiling.)
            val durationSeconds = grabber.lengthInTime / 1_000_000.0
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
        // Hard ceiling on output: bounds the decode even when the container
        // duration is unknown/misreported, and lets dispose() interrupt us.
        val maxBars = MAX_DURATION_SECONDS * barsPerSecond
        val bars = ArrayList<Double>(minOf(maxBars, 4096))
        var sum = 0.0
        var count = 0
        loop@ while (bars.size < maxBars && !Thread.currentThread().isInterrupted) {
            val frame = grabber.grabSamples() ?: break
            val buffer = frame.samples?.firstOrNull() as? ShortBuffer ?: continue
            while (buffer.hasRemaining()) {
                sum += abs(buffer.get().toInt()) / SHORT_FULL_SCALE
                count++
                if (count == samplesPerBar) {
                    bars.add(normalize(sum / count))
                    sum = 0.0
                    count = 0
                    if (bars.size >= maxBars) break@loop
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

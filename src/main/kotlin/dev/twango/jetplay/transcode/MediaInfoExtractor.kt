package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File

/**
 * Technical metadata for the "codec inspector" expandable header in the player.
 * All fields are nullable so the UI can simply skip anything FFmpeg couldn't
 * determine rather than render a wrong or placeholder value.
 */
data class MediaInfo(
    val codec: String?,
    val container: String?,
    val sampleRateHz: Int?,
    val channels: Int?,
    val channelLabel: String?,
    /** Only set when meaningful (PCM / lossless). Null for lossy codecs. */
    val bitDepth: String?,
    val bitrateBps: Long?,
    val durationMs: Long?,
    val sizeBytes: Long?,
)

/**
 * Probes an audio file's container/codec/stream details with the bundled FFmpeg.
 *
 * Only reads the header — no sample decoding — so it's cheap. Returns null when
 * the file has no readable audio stream, mirroring the graceful-empty contract
 * of [WaveformExtractor]; the UI then just shows the filename as before.
 */
object MediaInfoExtractor {

    private val log = Logger.getInstance(MediaInfoExtractor::class.java)

    // Codecs where a source bit depth is a real, honest property to surface.
    // Lossy codecs decode to float internally, so their "bit depth" would be
    // misleading — we omit it for those.
    private val LOSSLESS = setOf("flac", "alac", "wavpack", "truehd", "mlp", "tta", "als")

    /** Returns the file's audio metadata, or null if it has no readable audio. */
    fun extract(file: File): MediaInfo? {
        // RAW (not SHORT) so getSampleFormat() reports the true source format
        // instead of the S16 the SHORT path would always claim.
        val grabber = FFmpegFrameGrabber(file).apply { sampleMode = FrameGrabber.SampleMode.RAW }
        return try {
            grabber.start()
            val channels = grabber.audioChannels
            if (channels <= 0) return null // no audio stream

            val codec = grabber.audioCodecName?.takeIf { it.isNotBlank() }
            val durationMs = grabber.lengthInTime.takeIf { it > 0 }?.div(1000)
            val sizeBytes = file.length().takeIf { it > 0 }
            val bitrate = grabber.audioBitrate.toLong().takeIf { it > 0 } ?: computeBitrate(sizeBytes, durationMs)

            MediaInfo(
                codec = codec,
                container = grabber.format?.substringBefore(",")?.takeIf { it.isNotBlank() },
                sampleRateHz = grabber.sampleRate.takeIf { it > 0 },
                channels = channels,
                channelLabel = channelLabel(channels),
                bitDepth = bitDepth(codec, grabber.sampleFormat),
                bitrateBps = bitrate,
                durationMs = durationMs,
                sizeBytes = sizeBytes,
            )
        } catch (e: Exception) {
            log.warn("Media info extraction failed for ${file.name}", e)
            null
        } finally {
            safely("grabber.stop") { grabber.stop() }
            safely("grabber.release") { grabber.release() }
        }
    }

    private fun computeBitrate(sizeBytes: Long?, durationMs: Long?): Long? {
        if (sizeBytes == null || durationMs == null || durationMs <= 0) return null
        return sizeBytes * 8 * 1000 / durationMs
    }

    private fun channelLabel(channels: Int): String? = when {
        channels <= 0 -> null
        channels == 1 -> "mono"
        channels == 2 -> "stereo"
        channels == 6 -> "5.1"
        channels == 8 -> "7.1"
        else -> "$channels ch"
    }

    private val PCM_PATTERN = Regex("""^pcm_([fsu])(\d+)""")

    private fun bitDepth(codec: String?, sampleFormat: Int): String? {
        if (codec == null) return null
        // PCM encodes its depth in the codec name (e.g. pcm_s24le → 24-bit).
        // More accurate than the sample format, which widens pcm_s24le to S32.
        if (codec.startsWith("pcm_")) {
            val match = PCM_PATTERN.find(codec) ?: return null
            val bits = match.groupValues[2]
            return if (match.groupValues[1] == "f") "$bits-bit float" else "$bits-bit"
        }
        if (codec in LOSSLESS) {
            return when (sampleFormat) {
                avutil.AV_SAMPLE_FMT_U8, avutil.AV_SAMPLE_FMT_U8P -> "8-bit"
                avutil.AV_SAMPLE_FMT_S16, avutil.AV_SAMPLE_FMT_S16P -> "16-bit"
                avutil.AV_SAMPLE_FMT_S32, avutil.AV_SAMPLE_FMT_S32P -> "32-bit"
                avutil.AV_SAMPLE_FMT_FLT, avutil.AV_SAMPLE_FMT_FLTP -> "32-bit float"
                avutil.AV_SAMPLE_FMT_DBL, avutil.AV_SAMPLE_FMT_DBLP -> "64-bit float"
                else -> null
            }
        }
        return null
    }

    private inline fun safely(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn("$action failed", e)
        }
    }
}

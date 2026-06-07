package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avformat
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import java.util.Base64

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
    // Video-stream fields (null for audio-only files).
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val videoCodec: String? = null,
    val pixelFormat: String? = null,
    val videoBitrateBps: Long? = null,
    /** Embedded text tags (title/artist/album/…), in display order. */
    val tags: List<MediaTag> = emptyList(),
    /** Embedded cover art as a `data:` URL, or null when there is none. */
    val albumArt: String? = null,
)

/** One embedded metadata tag, already labeled for display. */
data class MediaTag(val label: String, val value: String)

/**
 * Probes a media file's container/codec/stream details with the bundled FFmpeg
 * (both audio and video streams).
 *
 * Only reads the header — no frame decoding — so it's cheap. Returns null when
 * the file has no readable audio or video stream, mirroring the graceful-empty
 * contract of [WaveformExtractor]; the UI then just shows the filename as before.
 */
object MediaInfoExtractor {

    private val log = Logger.getInstance(MediaInfoExtractor::class.java)

    // Codecs where a source bit depth is a real, honest property to surface.
    // Lossy codecs decode to float internally, so their "bit depth" would be
    // misleading — we omit it for those.
    private val LOSSLESS = setOf("flac", "alac", "wavpack", "truehd", "mlp", "tta", "als")

    // Cover art over this is skipped — it would only bloat the bridge payload,
    // and a blurred background needs no fidelity. Typical embedded art is < 1 MB.
    private const val MAX_ART_BYTES = 4_000_000

    // Embedded tags to surface, in display order, mapped from FFmpeg's
    // normalized (lowercase) metadata keys to a human label.
    private val TAG_FIELDS = listOf(
        "title" to "Title",
        "artist" to "Artist",
        "album" to "Album",
        "album_artist" to "Album artist",
        "composer" to "Composer",
        "track" to "Track",
        "disc" to "Disc",
        "date" to "Date",
        "genre" to "Genre",
        "publisher" to "Publisher",
        "comment" to "Comment",
        "rating" to "Rating",
    )

    private const val BITS_PER_BYTE = 8
    private const val MILLIS_PER_SECOND = 1000
    private const val CHANNELS_5_1 = 6
    private const val CHANNELS_7_1 = 8

    // Image-signature sniffing for embedded cover art.
    private const val IMAGE_SNIFF_MIN_BYTES = 12
    private const val WEBP_BRAND_OFFSET = 8
    private val SIG_JPEG = intArrayOf(0xFF, 0xD8, 0xFF)
    private val SIG_PNG = intArrayOf(0x89, 0x50, 0x4E, 0x47)
    private val SIG_GIF = intArrayOf(0x47, 0x49, 0x46)
    private val SIG_RIFF = intArrayOf(0x52, 0x49, 0x46, 0x46)
    private val SIG_WEBP = intArrayOf(0x57, 0x45, 0x42, 0x50)

    /** Returns the file's stream metadata, or null if it has no readable streams. */
    fun extract(file: File): MediaInfo? {
        // RAW modes so the *source* sample/pixel formats are reported; the SHORT/
        // COLOR defaults would report the decoder's output format instead.
        val grabber = FFmpegFrameGrabber(file).apply {
            sampleMode = FrameGrabber.SampleMode.RAW
            imageMode = FrameGrabber.ImageMode.RAW
        }
        return try {
            grabber.start()
            val channels = grabber.audioChannels
            val width = grabber.imageWidth
            val height = grabber.imageHeight
            val hasAudio = channels > 0
            val hasVideo = width > 0 && height > 0
            if (!hasAudio && !hasVideo) return null

            val durationMs = grabber.lengthInTime.takeIf { it > 0 }?.div(1000)
            val sizeBytes = file.length().takeIf { it > 0 }

            // Audio (canonical codec from the id, not the decoder name).
            val audioCodec = if (hasAudio) canonicalCodec(grabber.audioCodec) else null
            val audioBitrate = if (hasAudio) grabber.audioBitrate.toLong().takeIf { it > 0 } else null
            // The size/duration fallback is the whole-file bitrate, so it only
            // stands in for the audio bitrate when there is no video stream.
            val bitrate = audioBitrate ?: if (!hasVideo) computeBitrate(sizeBytes, durationMs) else null

            // Video.
            val videoCodec = if (hasVideo) canonicalCodec(grabber.videoCodec) else null
            val frameRate = if (hasVideo) grabber.videoFrameRate.takeIf { it.isFinite() && it > 0 } else null
            val pixelFormat = if (hasVideo) {
                avutil.av_get_pix_fmt_name(grabber.pixelFormat)?.getString()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            val videoBitrate = if (hasVideo) grabber.videoBitrate.toLong().takeIf { it > 0 } else null

            MediaInfo(
                codec = audioCodec,
                container = grabber.format?.substringBefore(",")?.takeIf { it.isNotBlank() },
                sampleRateHz = if (hasAudio) grabber.sampleRate.takeIf { it > 0 } else null,
                channels = if (hasAudio) channels else null,
                channelLabel = if (hasAudio) channelLabel(channels) else null,
                bitDepth = if (hasAudio) bitDepth(audioCodec, grabber.sampleFormat) else null,
                bitrateBps = bitrate,
                durationMs = durationMs,
                sizeBytes = sizeBytes,
                width = width.takeIf { hasVideo },
                height = height.takeIf { hasVideo },
                frameRate = frameRate,
                videoCodec = videoCodec,
                pixelFormat = pixelFormat,
                videoBitrateBps = videoBitrate,
                tags = buildTags(grabber.metadata ?: emptyMap()),
                albumArt = extractAlbumArt(grabber),
            )
        } catch (e: Exception) {
            log.warn("Media info extraction failed for ${file.name}", e)
            null
        } finally {
            safely("grabber.stop") { grabber.stop() }
            safely("grabber.release") { grabber.release() }
        }
    }

    // Canonical codec name from the codec id (e.g. "mp3", "h264"), not the
    // decoder name getAudioCodecName()/getVideoCodecName() returns ("mp3float").
    private fun canonicalCodec(codecId: Int): String? =
        avcodec.avcodec_get_name(codecId)?.getString()?.takeIf { it.isNotBlank() && it != "unknown" && it != "none" }

    private fun computeBitrate(sizeBytes: Long?, durationMs: Long?): Long? {
        if (sizeBytes == null || durationMs == null || durationMs <= 0) return null
        return sizeBytes * BITS_PER_BYTE * MILLIS_PER_SECOND / durationMs
    }

    private fun channelLabel(channels: Int): String? = when {
        channels <= 0 -> null
        channels == 1 -> "mono"
        channels == 2 -> "stereo"
        channels == CHANNELS_5_1 -> "5.1"
        channels == CHANNELS_7_1 -> "7.1"
        else -> "$channels ch"
    }

    private val PCM_PATTERN = Regex("""^pcm_([fsu])(\d+)""")

    private fun bitDepth(codec: String?, sampleFormat: Int): String? = when {
        codec == null -> null

        // PCM encodes its depth in the codec name (e.g. pcm_s24le → 24-bit).
        // More accurate than the sample format, which widens pcm_s24le to S32.
        codec.startsWith("pcm_") -> PCM_PATTERN.find(codec)?.let { match ->
            val bits = match.groupValues[2]
            if (match.groupValues[1] == "f") "$bits-bit float" else "$bits-bit"
        }

        codec in LOSSLESS -> when (sampleFormat) {
            avutil.AV_SAMPLE_FMT_U8, avutil.AV_SAMPLE_FMT_U8P -> "8-bit"
            avutil.AV_SAMPLE_FMT_S16, avutil.AV_SAMPLE_FMT_S16P -> "16-bit"
            avutil.AV_SAMPLE_FMT_S32, avutil.AV_SAMPLE_FMT_S32P -> "32-bit"
            avutil.AV_SAMPLE_FMT_FLT, avutil.AV_SAMPLE_FMT_FLTP -> "32-bit float"
            avutil.AV_SAMPLE_FMT_DBL, avutil.AV_SAMPLE_FMT_DBLP -> "64-bit float"
            else -> null
        }

        else -> null
    }

    /** Maps FFmpeg's metadata map to an ordered, labeled list of display tags. */
    internal fun buildTags(metadata: Map<String, String>): List<MediaTag> {
        if (metadata.isEmpty()) return emptyList()
        // FFmpeg keys are normally lowercase, but be tolerant of odd containers.
        val lower = metadata.entries.associate { it.key.lowercase() to it.value }
        return TAG_FIELDS.mapNotNull { (key, label) ->
            lower[key]?.trim()?.takeIf { it.isNotEmpty() }?.let { MediaTag(label, it) }
        }
    }

    /**
     * Reads the first attached-picture stream's raw image bytes (the packet data
     * IS the encoded cover) and returns it as a `data:` URL for the UI to blur
     * behind the player. No decode/re-encode — just sniff the type and base64.
     */
    private fun extractAlbumArt(grabber: FFmpegFrameGrabber): String? {
        return try {
            val oc = grabber.formatContext ?: return null
            (0 until oc.nb_streams()).firstNotNullOfOrNull { albumArtFromStream(oc.streams(it)) }
        } catch (e: Exception) {
            log.warn("Album art extraction failed", e)
            null
        }
    }

    /** Returns the cover-art `data:` URL from an attached-picture stream, or null. */
    private fun albumArtFromStream(stream: AVStream): String? {
        if (stream.disposition() and avformat.AV_DISPOSITION_ATTACHED_PIC == 0) return null
        val pkt = stream.attached_pic()
        val size = pkt.size()
        val data = pkt.data()
        if (size <= 0 || size > MAX_ART_BYTES || data == null) return null
        val bytes = ByteArray(size)
        data.capacity(size.toLong()).get(bytes)
        return sniffImageMime(bytes)?.let { "data:$it;base64,${Base64.getEncoder().encodeToString(bytes)}" }
    }

    private fun sniffImageMime(b: ByteArray): String? {
        if (b.size < IMAGE_SNIFF_MIN_BYTES) return null
        fun at(offset: Int, sig: IntArray) = sig.withIndex().all { (k, v) -> b[offset + k] == v.toByte() }
        return when {
            at(0, SIG_JPEG) -> "image/jpeg"
            at(0, SIG_PNG) -> "image/png"
            at(0, SIG_GIF) -> "image/gif"
            at(0, SIG_RIFF) && at(WEBP_BRAND_OFFSET, SIG_WEBP) -> "image/webp"
            else -> null
        }
    }

    private inline fun safely(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn("$action failed", e)
        }
    }
}

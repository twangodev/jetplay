package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import dev.twango.jetplay.media.MediaInfo
import dev.twango.jetplay.media.MediaTag
import kotlinx.coroutines.CancellationException
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avformat
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import java.util.Base64

/** Probes audio/video stream details via header-only FFmpeg reads; null when no readable stream exists. */
object MediaInfoExtractor {

    private val log = Logger.getInstance(MediaInfoExtractor::class.java)

    // Cap cover art: larger only bloats the bridge payload.
    private const val MAX_ART_BYTES = 4_000_000

    // FFmpeg metadata keys to display labels, in display order.
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
    private const val CHANNEL_LABEL_CAPACITY = 256L

    private const val IMAGE_SNIFF_MIN_BYTES = 12
    private const val WEBP_BRAND_OFFSET = 8
    private val SIG_JPEG = intArrayOf(0xFF, 0xD8, 0xFF)
    private val SIG_PNG = intArrayOf(0x89, 0x50, 0x4E, 0x47)
    private val SIG_GIF = intArrayOf(0x47, 0x49, 0x46)
    private val SIG_RIFF = intArrayOf(0x52, 0x49, 0x46, 0x46)
    private val SIG_WEBP = intArrayOf(0x57, 0x45, 0x42, 0x50)

    /** Returns the file's stream metadata, or null if it has no readable streams. */
    fun extract(file: File, checkCancelled: () -> Unit = {}): MediaInfo? {
        // RAW reports the source sample/pixel formats, not the decoder's output.
        val grabber = FFmpegFrameGrabber(file).apply {
            sampleMode = FrameGrabber.SampleMode.RAW
            imageMode = FrameGrabber.ImageMode.RAW
        }
        return try {
            checkCancelled()
            grabber.start()
            checkCancelled()
            val channels = grabber.audioChannels
            val width = grabber.imageWidth
            val height = grabber.imageHeight
            val hasAudio = channels > 0
            val hasVideo = width > 0 && height > 0
            if (!hasAudio && !hasVideo) return null
            val audioParameters = if (hasAudio) audioParameters(grabber) else null

            val durationMs = grabber.lengthInTime.takeIf { it > 0 }?.div(1000)
            val sizeBytes = file.length().takeIf { it > 0 }

            val audioCodec = if (hasAudio) canonicalCodec(grabber.audioCodec) else null
            val audioBitrate = if (hasAudio) grabber.audioBitrate.toLong().takeIf { it > 0 } else null
            // Whole-file bitrate fallback is valid for audio only when there is no video.
            val bitrate = audioBitrate ?: if (!hasVideo) computeBitrate(sizeBytes, durationMs) else null

            val videoCodec = if (hasVideo) canonicalCodec(grabber.videoCodec) else null
            val frameRate = if (hasVideo) grabber.videoFrameRate.takeIf { it.isFinite() && it > 0 } else null
            val pixelFormat = if (hasVideo) {
                avutil.av_get_pix_fmt_name(grabber.pixelFormat)?.string?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            val videoBitrate = if (hasVideo) grabber.videoBitrate.toLong().takeIf { it > 0 } else null

            MediaInfo(
                codec = audioCodec,
                container = grabber.format?.substringBefore(",")?.takeIf { it.isNotBlank() },
                sampleRateHz = if (hasAudio) grabber.sampleRate.takeIf { it > 0 } else null,
                channels = if (hasAudio) channels else null,
                channelLabel = audioParameters?.let(::channelLabel),
                bitDepth = audioParameters?.let(::bitDepth),
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Media info extraction failed for ${file.name}", e)
            null
        } finally {
            log.safely("grabber.stop") { grabber.stop() }
            log.safely("grabber.release") { grabber.release() }
        }
    }

    // Codec name from the id, not the decoder name the *CodecName getters return.
    private fun canonicalCodec(codecId: Int): String? =
        avcodec.avcodec_get_name(codecId)?.string?.takeIf { it.isNotBlank() && it != "unknown" && it != "none" }

    private fun computeBitrate(sizeBytes: Long?, durationMs: Long?): Long? {
        if (sizeBytes == null || durationMs == null || durationMs <= 0) return null
        return sizeBytes * BITS_PER_BYTE * MILLIS_PER_SECOND / durationMs
    }

    private fun audioParameters(grabber: FFmpegFrameGrabber): AVCodecParameters? {
        val context = grabber.formatContext ?: return null
        val stream = (0 until context.nb_streams()).firstOrNull { index ->
            context.streams(index).codecpar().codec_type() == avutil.AVMEDIA_TYPE_AUDIO &&
                (grabber.audioStream < 0 || grabber.audioStream == index)
        } ?: return null
        return context.streams(stream).codecpar()
    }

    private fun channelLabel(parameters: AVCodecParameters): String? {
        if (parameters.ch_layout().nb_channels() <= 0) return null
        return BytePointer(CHANNEL_LABEL_CAPACITY).use { label ->
            val result = avutil.av_channel_layout_describe(parameters.ch_layout(), label, CHANNEL_LABEL_CAPACITY)
            // The allocated pointer's limit is its capacity; FFmpeg wrote a NUL-terminated C string.
            if (result >= 0) label.limit(0).string else null
        }
    }

    private fun bitDepth(parameters: AVCodecParameters): String? {
        val codec = parameters.codec_id()
        val properties = avcodec.avcodec_descriptor_get(codec)?.props() ?: 0
        if (properties and avcodec.AV_CODEC_PROP_LOSSLESS == 0) return null
        // Decoded storage (for example s32 for 24-bit FLAC) is not the source bit depth.
        val bits = parameters.bits_per_raw_sample().takeIf { it > 0 }
            ?: avcodec.av_get_exact_bits_per_sample(codec).takeIf { it > 0 }
            ?: return null
        val packed = avutil.av_get_packed_sample_fmt(parameters.format())
        val floating = packed == avutil.AV_SAMPLE_FMT_FLT || packed == avutil.AV_SAMPLE_FMT_DBL
        return if (floating) "$bits-bit float" else "$bits-bit"
    }

    internal fun buildTags(metadata: Map<String, String>): List<MediaTag> {
        if (metadata.isEmpty()) return emptyList()
        // Tolerate non-lowercase keys from odd containers.
        val lower = metadata.entries.associate { it.key.lowercase() to it.value }
        return TAG_FIELDS.mapNotNull { (key, label) ->
            lower[key]?.trim()?.takeIf { it.isNotEmpty() }?.let { MediaTag(label, it) }
        }
    }

    /** First attached-picture stream's raw bytes as a `data:` URL, with no decode/re-encode. */
    private fun extractAlbumArt(grabber: FFmpegFrameGrabber): String? {
        return try {
            val oc = grabber.formatContext ?: return null
            (0 until oc.nb_streams()).firstNotNullOfOrNull { albumArtFromStream(oc.streams(it)) }
        } catch (e: Exception) {
            log.warn("Album art extraction failed", e)
            null
        }
    }

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
}

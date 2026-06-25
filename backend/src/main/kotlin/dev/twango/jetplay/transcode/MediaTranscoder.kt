package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import dev.twango.jetplay.JetPlayConstants
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.nio.file.Files

object MediaTranscoder {

    private val log = Logger.getInstance(MediaTranscoder::class.java)

    private const val DEFAULT_VIDEO_BITRATE = 2_000_000
    private const val DEFAULT_FRAME_RATE = 30.0
    private const val DEFAULT_GOP_SIZE = 120
    private val VP9_THREADS = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
    private const val OPUS_BITRATE = 128_000
    private const val OPUS_SAMPLE_RATE = 48_000
    private const val PROGRESS_COMPLETE = 100.0
    private const val PROGRESS_MAX = 99.9
    private const val PROGRESS_PRECISION = 10
    private const val INDETERMINATE_TENTH = -1L
    private const val REPORTED_INDETERMINATE_TENTH = -2L

    // Headerless raw codec streams need an explicit demuxer + sample rate + channels.
    private data class RawAudioHint(val format: String, val sampleRate: Int, val channels: Int)

    private val RAW_AUDIO_HINTS = mapOf(
        "pcmu" to RawAudioHint("mulaw", 8000, 1),
        "ulaw" to RawAudioHint("mulaw", 8000, 1),
        "pcma" to RawAudioHint("alaw", 8000, 1),
        "alaw" to RawAudioHint("alaw", 8000, 1),
        "g722" to RawAudioHint("g722", 16000, 1),
        "slin" to RawAudioHint("s16le", 8000, 1),
    )

    /** Must stay in sync with the shared classifier. */
    internal val rawAudioExtensions: Set<String> get() = RAW_AUDIO_HINTS.keys

    fun transcode(inputFile: File, onProgress: (Double) -> Unit = {}): File {
        val outputFile = Files.createTempFile("jetplay-", ".webm").toFile().apply { deleteOnExit() }

        val grabber = FFmpegFrameGrabber(inputFile).also { applyRawHints(it, inputFile.extension) }
        var grabberStarted = false
        var recorder: FFmpegFrameRecorder? = null
        var recorderStarted = false
        try {
            grabber.start()
            grabberStarted = true

            val hasVideo = grabber.videoCodec > 0
            val hasAudio = grabber.audioChannels > 0
            val totalMicroseconds = grabber.lengthInTime

            recorder = buildRecorder(outputFile, grabber, hasVideo, hasAudio)
            recorder.start()
            recorderStarted = true

            runFrameLoop(grabber, recorder, hasAudio, totalMicroseconds, onProgress)
        } finally {
            if (recorderStarted) safely("recorder.stop") { recorder!!.stop() }
            recorder?.let { safely("recorder.release") { it.release() } }
            if (grabberStarted) safely("grabber.stop") { grabber.stop() }
            safely("grabber.release") { grabber.release() }
        }

        onProgress(PROGRESS_COMPLETE)
        log.info("Transcoded ${inputFile.name} -> ${outputFile.name} (${outputFile.length() / JetPlayConstants.BYTES_PER_KB}KB)")
        return outputFile
    }

    private fun applyRawHints(grabber: FFmpegFrameGrabber, extension: String) {
        val hint = RAW_AUDIO_HINTS[extension.lowercase()] ?: return
        grabber.format = hint.format
        grabber.sampleRate = hint.sampleRate
        grabber.audioChannels = hint.channels
    }

    private fun buildRecorder(
        outputFile: File,
        grabber: FFmpegFrameGrabber,
        hasVideo: Boolean,
        hasAudio: Boolean,
    ): FFmpegFrameRecorder {
        val recorder = FFmpegFrameRecorder(
            outputFile,
            grabber.imageWidth,
            grabber.imageHeight,
            grabber.audioChannels,
        )
        recorder.format = "webm"
        if (hasVideo) {
            recorder.videoCodec = avcodec.AV_CODEC_ID_VP9
            recorder.videoBitrate = grabber.videoBitrate.takeIf { it > 0 } ?: DEFAULT_VIDEO_BITRATE
            recorder.frameRate = grabber.frameRate.takeIf { it > 0 } ?: DEFAULT_FRAME_RATE
            recorder.gopSize = DEFAULT_GOP_SIZE
            // libvpx's default deadline is so slow an HD clip looks hung.
            recorder.setVideoOption("deadline", "realtime")
            recorder.setVideoOption("cpu-used", "8")
            recorder.setVideoOption("row-mt", "1")
            recorder.setVideoOption("threads", VP9_THREADS.toString())
        }
        if (hasAudio) {
            recorder.audioCodec = avcodec.AV_CODEC_ID_OPUS
            recorder.audioBitrate = OPUS_BITRATE
            recorder.sampleRate = OPUS_SAMPLE_RATE
            recorder.audioChannels = grabber.audioChannels
        }
        return recorder
    }

    private fun runFrameLoop(
        grabber: FFmpegFrameGrabber,
        recorder: FFmpegFrameRecorder,
        hasAudio: Boolean,
        totalMicroseconds: Long,
        onProgress: (Double) -> Unit,
    ) {
        var lastReportedTenth = INDETERMINATE_TENTH
        while (true) {
            val frame = grabber.grabFrame(hasAudio, true, true, false, false) ?: break
            recorder.record(frame)
            lastReportedTenth = reportProgress(grabber.timestamp, totalMicroseconds, lastReportedTenth, onProgress)
        }
    }

    private fun reportProgress(
        timestamp: Long,
        totalMicroseconds: Long,
        lastReportedTenth: Long,
        onProgress: (Double) -> Unit,
    ): Long {
        if (totalMicroseconds <= 0) {
            if (lastReportedTenth == INDETERMINATE_TENTH) onProgress(-1.0)
            return REPORTED_INDETERMINATE_TENTH
        }
        val pct = (timestamp.toDouble() * PROGRESS_COMPLETE / totalMicroseconds).coerceIn(0.0, PROGRESS_MAX)
        val tenth = (pct * PROGRESS_PRECISION).toLong()
        if (tenth != lastReportedTenth) onProgress(pct)
        return tenth
    }

    private inline fun safely(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn("$action failed", e)
        }
    }
}

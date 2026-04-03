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
    private const val OPUS_BITRATE = 128_000
    private const val OPUS_SAMPLE_RATE = 48_000
    private const val PROGRESS_COMPLETE = 100.0
    private const val PROGRESS_MAX = 99.9
    private const val PROGRESS_PRECISION = 10

    // Formats that JCEF (Chromium) can play natively without transcoding
    private val JCEF_NATIVE_EXTENSIONS = setOf(
        // video
        "webm",
        "ogv",
        // audio
        "ogg",
        "oga",
        "opus",
        "wav",
        "flac",
        "mp3",
    )

    fun needsTranscoding(extension: String?): Boolean = extension?.lowercase() !in JCEF_NATIVE_EXTENSIONS

    fun transcode(inputFile: File, onProgress: (Double) -> Unit = {}): File {
        val outputFile = Files.createTempFile("jetplay-", ".webm").toFile().apply { deleteOnExit() }

        val grabber = FFmpegFrameGrabber(inputFile)
        grabber.start()

        val hasVideo = grabber.videoCodec > 0
        val hasAudio = grabber.audioChannels > 0
        val totalMicroseconds = grabber.lengthInTime

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
        }
        if (hasAudio) {
            recorder.audioCodec = avcodec.AV_CODEC_ID_OPUS
            recorder.audioBitrate = OPUS_BITRATE
            recorder.sampleRate = OPUS_SAMPLE_RATE
            recorder.audioChannels = grabber.audioChannels
        }
        recorder.start()

        var lastReportedTenth = -1L
        try {
            while (true) {
                val frame = grabber.grabFrame(
                    hasAudio,
                    true,
                    true,
                    false,
                    false,
                ) ?: break
                recorder.record(frame)

                if (totalMicroseconds > 0) {
                    val pct = (grabber.timestamp.toDouble() * PROGRESS_COMPLETE / totalMicroseconds).coerceIn(0.0, PROGRESS_MAX)
                    val tenth = (pct * PROGRESS_PRECISION).toLong()
                    if (tenth != lastReportedTenth) {
                        lastReportedTenth = tenth
                        onProgress(pct)
                    }
                } else if (lastReportedTenth != -1L) {
                    lastReportedTenth = -1L
                    onProgress(-1.0)
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            grabber.stop()
            grabber.release()
        }

        onProgress(PROGRESS_COMPLETE)
        log.info("Transcoded ${inputFile.name} -> ${outputFile.name} (${outputFile.length() / JetPlayConstants.BYTES_PER_KB}KB)")
        return outputFile
    }
}

package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.nio.file.Files

object MediaTranscoder {

    private val log = Logger.getInstance(MediaTranscoder::class.java)

    // Formats that JCEF (Chromium) can play natively without transcoding
    private val JCEF_NATIVE_EXTENSIONS = setOf(
        "webm", "ogv",  // video
        "ogg", "oga", "opus", "wav", "flac", "mp3" // audio
    )

    fun needsTranscoding(extension: String?): Boolean {
        return extension?.lowercase() !in JCEF_NATIVE_EXTENSIONS
    }

    fun transcode(inputFile: File, onProgress: (Double) -> Unit = {}): File {
        val outputFile = Files.createTempFile("jetplay-", ".webm").toFile().apply { deleteOnExit() }

        val grabber = FFmpegFrameGrabber(inputFile)
        grabber.start()

        val hasVideo = grabber.videoCodec > 0
        val hasAudio = grabber.audioChannels > 0
        val totalMicroseconds = grabber.lengthInTime

        val recorder = FFmpegFrameRecorder(outputFile, grabber.imageWidth, grabber.imageHeight, grabber.audioChannels)
        recorder.format = "webm"
        if (hasVideo) {
            recorder.videoCodec = avcodec.AV_CODEC_ID_VP9
            recorder.videoBitrate = grabber.videoBitrate.takeIf { it > 0 } ?: 2_000_000
            recorder.frameRate = grabber.frameRate.takeIf { it > 0 } ?: 30.0
            recorder.gopSize = 120
        }
        if (hasAudio) {
            recorder.audioCodec = avcodec.AV_CODEC_ID_OPUS
            recorder.audioBitrate = 128_000
            recorder.sampleRate = 48000
            recorder.audioChannels = grabber.audioChannels
        }
        recorder.start()

        var lastReportedTenth = -1L
        try {
            while (true) {
                val frame = grabber.grabFrame(hasAudio, true, true, false, false) ?: break
                recorder.record(frame)

                if (totalMicroseconds > 0) {
                    val pct = (grabber.timestamp.toDouble() * 100.0 / totalMicroseconds).coerceIn(0.0, 99.9)
                    val tenth = (pct * 10).toLong()
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

        onProgress(100.0)
        log.info("Transcoded ${inputFile.name} -> ${outputFile.name} (${outputFile.length() / 1024}KB)")
        return outputFile
    }
}

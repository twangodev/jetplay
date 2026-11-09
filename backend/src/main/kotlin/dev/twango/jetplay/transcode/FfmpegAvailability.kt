package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import org.bytedeco.ffmpeg.ffmpeg
import org.bytedeco.javacpp.Loader

object FfmpegAvailability {

    private val log = Logger.getInstance(FfmpegAvailability::class.java)

    val executable: String by lazy { Loader.load(ffmpeg::class.java) }

    val available: Boolean by lazy {
        try {
            Class.forName("org.bytedeco.ffmpeg.global.avutil")
            org.bytedeco.ffmpeg.global.avutil.avutil_version()
            executable
            true
        } catch (e: Throwable) {
            log.warn("FFmpeg native libraries not available", e)
            false
        }
    }
}

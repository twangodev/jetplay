package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger

object FfmpegAvailability {

    private val log = Logger.getInstance(FfmpegAvailability::class.java)

    val available: Boolean by lazy {
        try {
            Class.forName("org.bytedeco.ffmpeg.global.avutil")
            org.bytedeco.ffmpeg.global.avutil.avutil_version()
            true
        } catch (e: Throwable) {
            log.warn("FFmpeg native libraries not available", e)
            false
        }
    }
}

package dev.twango.jetplay.transcode

import java.io.File

object TranscodeRunner {

    /** Runs ffmpeg, invoking onProgress(percent). Returns the transcoded File; throws on failure. */
    fun transcode(inputFile: File, onProgress: (Double) -> Unit): File =
        MediaTranscoder.transcode(inputFile, onProgress)
}

package dev.twango.jetplay.media

object MediaClassification {

    // Video subset of extensions registered in plugin.xml — keep in sync
    private val VIDEO_EXTENSIONS = setOf(
        "mp4",
        "m4v",
        "mkv",
        "avi",
        "mov",
        "wmv",
        "flv",
        "webm",
        "ogv",
        "ts",
        "mts",
        "m2ts",
        "3gp",
        "ivf",
    )

    // Chromium can play these natively, so JCEF needs no transcoding.
    private val JCEF_NATIVE_EXTENSIONS = setOf(
        "webm",
        "ogv",
        "ogg",
        "oga",
        "opus",
        "wav",
        "flac",
        "mp3",
    )

    // Headerless raw codec streams that need explicit demuxer hints; backend supplies the demuxer config.
    val rawAudioExtensions: Set<String> = setOf(
        "pcmu",
        "ulaw",
        "pcma",
        "alaw",
        "g722",
        "gsm",
        "sln",
    )

    fun isVideo(extension: String): Boolean = extension.lowercase() in VIDEO_EXTENSIONS

    fun needsTranscoding(extension: String?): Boolean = extension?.lowercase() !in JCEF_NATIVE_EXTENSIONS
}

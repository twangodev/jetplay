package dev.twango.jetplay.media

object MediaClassification {

    // Video extensions registered by the media file type in the frontend descriptor.
    // .ts is excluded to avoid clobbering TypeScript files.
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
        "mts",
        "m2ts",
        "m2t",
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

    // Headerless raw codec streams that need explicit demuxer hints.
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

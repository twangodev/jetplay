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
    )

    fun isVideo(extension: String): Boolean = extension.lowercase() in VIDEO_EXTENSIONS
}

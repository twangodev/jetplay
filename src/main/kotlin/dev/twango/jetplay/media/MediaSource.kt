package dev.twango.jetplay.media

interface MediaSource {
    val fileName: String
    val extension: String
    val isVideo: Boolean
    val needsTranscoding: Boolean
    fun resolvePlayableUrl(): String
}

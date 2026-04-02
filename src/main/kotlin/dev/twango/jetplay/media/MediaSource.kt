package dev.twango.jetplay.media

import java.io.File

interface MediaSource {
    val fileName: String
    val extension: String
    val isVideo: Boolean
    val needsTranscoding: Boolean
    val isRemote: Boolean
    fun resolvePlayableUrl(): String
    fun toLocalFile(): File
}

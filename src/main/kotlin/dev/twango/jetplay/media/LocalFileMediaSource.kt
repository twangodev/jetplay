package dev.twango.jetplay.media

import com.intellij.openapi.vfs.VirtualFile
import dev.twango.jetplay.transcode.MediaTranscoder
import java.io.File

class LocalFileMediaSource(private val file: VirtualFile) : MediaSource {

    override val fileName: String = file.name

    override val extension: String = file.extension?.lowercase() ?: ""

    override val isVideo: Boolean = MediaClassification.isVideo(extension)

    override val needsTranscoding: Boolean = MediaTranscoder.needsTranscoding(extension)

    override val isRemote: Boolean = false

    override fun resolvePlayableUrl(): String = file.toNioPath().toUri().toString()

    override fun toLocalFile(): File = file.toNioPath().toFile()
}

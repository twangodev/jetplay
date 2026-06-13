package dev.twango.jetplay.media

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/** Frontend view of a media file; VirtualFile carries identity for rpcId. */
class EditorMediaSource(val file: VirtualFile) : MediaSource {
    override val fileName: String = file.name
    override val extension: String = file.extension?.lowercase() ?: ""
    override val isVideo: Boolean = MediaClassification.isVideo(extension)
    override val needsTranscoding: Boolean = MediaClassification.needsTranscoding(extension)
    override val isRemote: Boolean = file.fileSystem !is LocalFileSystem

    /** Non-null only when the bytes are readable in this process. */
    fun localFileOrNull(): File? =
        if (!isRemote) runCatching { file.toNioPath().toFile() }.getOrNull()?.takeIf { it.isFile } else null
}

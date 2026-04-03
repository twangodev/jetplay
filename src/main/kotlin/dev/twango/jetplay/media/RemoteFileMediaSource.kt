package dev.twango.jetplay.media

import com.intellij.openapi.vfs.VirtualFile
import dev.twango.jetplay.transcode.MediaTranscoder
import java.io.File
import java.io.InputStream

class RemoteFileMediaSource(private val file: VirtualFile) : MediaSource {

    override val fileName: String = file.name

    override val extension: String = file.extension?.lowercase() ?: ""

    override val isVideo: Boolean = MediaClassification.isVideo(extension)

    override val needsTranscoding: Boolean = MediaTranscoder.needsTranscoding(extension)

    override val isRemote: Boolean = true

    val fileSize: Long = file.length

    @Volatile
    private var localFile: File? = null

    fun inputStream(): InputStream = file.inputStream

    fun setLocalFile(file: File) {
        localFile = file
    }

    override fun resolvePlayableUrl(): String = localFile?.toURI()?.toString()
        ?: error("Remote file not yet downloaded")

    override fun toLocalFile(): File = localFile ?: error("Remote file not yet downloaded")
}

package dev.twango.jetplay.editor

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.twango.jetplay.media.MediaClassification
import javax.swing.Icon

class MediaFileIconProvider :
    FileIconProvider,
    DumbAware {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file.fileType != MediaFileType.INSTANCE) return null
        val extension = file.extension ?: return null
        return if (MediaClassification.isVideo(extension)) JetplayIcons.Video else JetplayIcons.Audio
    }
}

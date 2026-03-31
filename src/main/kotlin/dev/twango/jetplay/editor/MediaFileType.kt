package dev.twango.jetplay.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.UserBinaryFileType
import javax.swing.Icon

class MediaFileType private constructor() : UserBinaryFileType() {

    companion object {
        @JvmStatic
        val INSTANCE = MediaFileType()
    }

    override fun getName(): String = "Media"
    override fun getDescription(): String = "Media files (audio/video)"
    override fun getDefaultExtension(): String = "mp4"
    override fun getIcon(): Icon = AllIcons.FileTypes.Any_type
}

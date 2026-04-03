package dev.twango.jetplay.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.UserBinaryFileType
import dev.twango.jetplay.JetPlayBundle
import javax.swing.Icon

class MediaFileType private constructor() : UserBinaryFileType() {

    companion object {
        @JvmStatic
        val INSTANCE = MediaFileType()
    }

    override fun getName(): String = JetPlayBundle.message("filetype.name")
    override fun getDescription(): String = JetPlayBundle.message("filetype.description")
    override fun getDefaultExtension(): String = "mp4"
    override fun getIcon(): Icon = AllIcons.FileTypes.Any_type
}

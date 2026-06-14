package dev.twango.jetplay.editor

import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import dev.twango.jetplay.JetPlayBundle
import dev.twango.jetplay.media.EditorMediaSource
import dev.twango.jetplay.star.StarReminder

class MediaFileEditorProvider :
    FileEditorProvider,
    DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean = file.fileType == MediaFileType.INSTANCE

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        if (!canRenderJcefHere()) {
            log.warn("JCEF unavailable or on the Remote Dev host; opening ${file.name} in the fallback editor")
            return MediaErrorEditor(file, JetPlayBundle.message("error.jcef.unavailable"))
        }
        StarReminder.maybeShow(project)
        return MediaFileEditor(project, file, EditorMediaSource(file))
    }

    private fun canRenderJcefHere(): Boolean = !AppMode.isRemoteDevHost() && JBCefApp.isSupported()

    override fun getEditorTypeId(): String = "media-player"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    private companion object {
        private val log = Logger.getInstance(MediaFileEditorProvider::class.java)
    }
}

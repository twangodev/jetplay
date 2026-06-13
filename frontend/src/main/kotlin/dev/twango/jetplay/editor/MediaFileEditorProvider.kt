package dev.twango.jetplay.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.twango.jetplay.media.EditorMediaSource
import dev.twango.jetplay.star.StarReminder

class MediaFileEditorProvider :
    FileEditorProvider,
    DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean = file.fileType == MediaFileType.INSTANCE

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val source = EditorMediaSource(file)
        StarReminder.maybeShow(project)
        return MediaFileEditor(project, file, source)
    }

    override fun getEditorTypeId(): String = "media-player"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

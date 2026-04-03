package dev.twango.jetplay.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import dev.twango.jetplay.media.LocalFileMediaSource
import dev.twango.jetplay.media.RemoteFileMediaSource

class MediaFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.fileType == MediaFileType.INSTANCE
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val source = if (file.fileSystem is LocalFileSystem) {
            LocalFileMediaSource(file)
        } else {
            RemoteFileMediaSource(file)
        }
        return MediaFileEditor(project, file, source)
    }

    override fun getEditorTypeId(): String = "media-player"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

package dev.twango.jetplay.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp

class MediaFileEditorProvider : FileEditorProvider, DumbAware {

    companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "webm")
        private val AUDIO_EXTENSIONS = setOf("mp3", "ogg", "wav")
        val SUPPORTED_EXTENSIONS = VIDEO_EXTENSIONS + AUDIO_EXTENSIONS

        fun isVideo(extension: String): Boolean = extension.lowercase() in VIDEO_EXTENSIONS
    }

    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (!JBCefApp.isSupported()) return false
        val ext = file.extension?.lowercase() ?: return false
        return ext in SUPPORTED_EXTENSIONS
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return MediaFileEditor(file)
    }

    override fun getEditorTypeId(): String = "media-player"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
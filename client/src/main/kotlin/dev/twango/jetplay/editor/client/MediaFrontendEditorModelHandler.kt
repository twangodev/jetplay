package dev.twango.jetplay.editor.client

import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.ide.model.FileEditorModel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rdclient.fileEditors.AsyncFrontendFileEditorModelHandler
import dev.twango.jetplay.editor.MediaFileEditorProvider
import dev.twango.jetplay.editor.MediaFileType

// A media file has no backend text model to bind (unlike text editors), so we ignore the model
// param and build the editor from MediaFileEditorProvider, which returns the live JCEF player on the client.
class MediaFrontendEditorModelHandler : AsyncFrontendFileEditorModelHandler {

    override fun accept(project: Project, file: VirtualFile, model: FileEditorModel): Boolean =
        file.fileType == MediaFileType.INSTANCE

    override fun createEditorWithProvider(
        project: Project,
        lifetime: Lifetime,
        file: VirtualFile,
        model: FileEditorModel,
    ): FileEditorWithProvider = buildEditor(project, file)

    override suspend fun createEditorWithProviderAsync(
        session: ClientProjectSession,
        file: VirtualFile,
        editorLifetime: Lifetime,
        model: FileEditorModel,
    ): FileEditorWithProvider = buildEditor(session.project, file)

    private fun buildEditor(project: Project, file: VirtualFile): FileEditorWithProvider {
        val provider = MediaFileEditorProvider()
        val editor = provider.createEditor(project, file)
        return FileEditorWithProvider(editor, provider)
    }
}

package dev.twango.jetplay.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.twango.jetplay.JetPlayBundle
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Plain-Swing fallback editor for when JCEF (the media renderer) is unavailable. Shown instead of
 * an empty/broken browser pane so the failure is explicit rather than an indefinite blank tab.
 */
class MediaErrorEditor(private val file: VirtualFile, message: String) :
    UserDataHolderBase(),
    FileEditor {

    private val component: JComponent = JBLabel(
        "<html><div style='text-align:center'>$message</div></html>",
        JBLabel.CENTER,
    ).apply {
        border = JBUI.Borders.empty(PADDING)
    }

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = component
    override fun getName(): String = JetPlayBundle.message("editor.name")
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun getFile(): VirtualFile = file
    override fun dispose() = Unit

    private companion object {
        private const val PADDING = 24
    }
}

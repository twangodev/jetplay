package dev.twango.jetplay.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import dev.twango.jetplay.JetPlayBundle
import dev.twango.jetplay.browser.PlayerBridge
import dev.twango.jetplay.browser.PlayerHtmlLoader
import dev.twango.jetplay.media.MediaSource
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

class MediaFileEditor(private val project: Project, private val file: VirtualFile, private val source: MediaSource) :
    UserDataHolderBase(),
    FileEditor {

    private val browser = JBCefBrowser()
    private val bridge = PlayerBridge(browser)
    private val htmlLoader = PlayerHtmlLoader(bridge)
    private val mediaLoader = MediaLoader(project, source, bridge, htmlLoader)

    private val component: JComponent = JPanel(BorderLayout()).apply {
        add(browser.component, BorderLayout.CENTER)
    }

    init {
        mediaLoader.load()
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

    override fun dispose() {
        mediaLoader.dispose()
        bridge.dispose()
    }
}

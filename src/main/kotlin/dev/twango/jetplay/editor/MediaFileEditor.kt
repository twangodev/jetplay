package dev.twango.jetplay.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class MediaFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val browser: JBCefBrowser = JBCefBrowser()

    init {
        val tag = if (MediaFileEditorProvider.isVideo(file.extension ?: "")) "video" else "audio"
        val fileUrl = file.toNioPath().toUri().toString()
        val bgColor = com.intellij.ui.JBColor.background().let {
            String.format("#%02x%02x%02x", it.red, it.green, it.blue)
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <style>
                body {
                  margin: 0;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  height: 100vh;
                  background: $bgColor;
                  overflow: hidden;
                }
                video, audio {
                  max-width: 100%;
                  max-height: 100%;
                }
              </style>
            </head>
            <body>
              <$tag controls src="$fileUrl"></$tag>
            </body>
            </html>
        """.trimIndent()

        browser.loadHTML(html)
    }

    override fun getComponent(): JComponent = browser.component
    override fun getPreferredFocusedComponent(): JComponent = browser.component
    override fun getName(): String = "Media Player"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file

    override fun dispose() {
        Disposer.dispose(browser)
    }
}
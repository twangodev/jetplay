package dev.twango.jetplay.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import dev.twango.jetplay.browser.PlayerBridge
import dev.twango.jetplay.browser.PlayerConfig
import dev.twango.jetplay.browser.PlayerHtmlLoader
import dev.twango.jetplay.media.LocalFileMediaSource
import dev.twango.jetplay.transcode.TranscodeSession
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

class MediaFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val browser = JBCefBrowser()
    private val bridge = PlayerBridge(browser)
    private val htmlLoader = PlayerHtmlLoader(bridge)
    private val source = LocalFileMediaSource(file)
    private var transcodeSession: TranscodeSession? = null

    private val component: JComponent = JPanel(BorderLayout()).apply {
        add(browser.component, BorderLayout.CENTER)
    }

    init {
        if (source.needsTranscoding) {
            htmlLoader.load(
                PlayerConfig(
                    state = "loading",
                    isVideo = source.isVideo,
                    fileName = source.fileName,
                    fileExtension = source.extension,
                    transcodingReason = "${source.extension.uppercase()} uses codecs not natively supported by the embedded browser. Converting to WebM (VP9/Opus) for playback."
                )
            )
            transcodeSession = TranscodeSession(source.toLocalFile(), bridge).also { it.start() }
        } else {
            htmlLoader.load(
                PlayerConfig(
                    isVideo = source.isVideo,
                    fileName = source.fileName,
                    fileExtension = source.extension,
                    mediaUrl = source.resolvePlayableUrl()
                )
            )
        }
    }

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = component
    override fun getName(): String = "Media Player"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file

    override fun dispose() {
        transcodeSession?.cancel()
        bridge.dispose()
    }
}

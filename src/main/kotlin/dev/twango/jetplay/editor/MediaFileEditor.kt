package dev.twango.jetplay.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import dev.twango.jetplay.browser.PlayerBridge
import dev.twango.jetplay.browser.PlayerConfig
import dev.twango.jetplay.browser.PlayerHtmlLoader
import dev.twango.jetplay.media.MediaSource
import dev.twango.jetplay.media.RemoteFileMediaSource
import dev.twango.jetplay.transcode.TranscodeSession
import dev.twango.jetplay.transfer.DownloadSession
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

class MediaFileEditor(
    private val file: VirtualFile,
    private val source: MediaSource
) : UserDataHolderBase(), FileEditor {

    private val browser = JBCefBrowser()
    private val bridge = PlayerBridge(browser)
    private val htmlLoader = PlayerHtmlLoader(bridge)
    private var downloadSession: DownloadSession? = null
    private var transcodeSession: TranscodeSession? = null

    private val component: JComponent = JPanel(BorderLayout()).apply {
        add(browser.component, BorderLayout.CENTER)
    }

    init {
        if (source.isRemote) {
            startDownload()
        } else if (source.needsTranscoding) {
            startTranscoding()
        } else {
            playDirectly()
        }
    }

    private fun startDownload() {
        htmlLoader.load(
            PlayerConfig(
                state = "downloading",
                isVideo = source.isVideo,
                fileName = source.fileName,
                fileExtension = source.extension,
                downloadingReason = "This file is on a remote host. Downloading to enable local playback."
            )
        )
        downloadSession = DownloadSession(source as RemoteFileMediaSource, bridge) {
            if (source.needsTranscoding) {
                startTranscoding()
            } else {
                bridge.mediaReady(source.resolvePlayableUrl())
            }
        }.also { it.start() }
    }

    private fun startTranscoding() {
        if (source.isRemote) {
            bridge.executeJs("window.jetplayStartTranscoding?.()")
        } else {
            htmlLoader.load(
                PlayerConfig(
                    state = "loading",
                    isVideo = source.isVideo,
                    fileName = source.fileName,
                    fileExtension = source.extension,
                    transcodingReason = "${source.extension.uppercase()} uses codecs not natively supported by the embedded browser. Converting to WebM (VP9/Opus) for playback."
                )
            )
        }
        transcodeSession = TranscodeSession(source.toLocalFile(), bridge).also { it.start() }
    }

    private fun playDirectly() {
        htmlLoader.load(
            PlayerConfig(
                isVideo = source.isVideo,
                fileName = source.fileName,
                fileExtension = source.extension,
                mediaUrl = source.resolvePlayableUrl()
            )
        )
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
        downloadSession?.cancel()
        transcodeSession?.cancel()
        bridge.dispose()
    }
}

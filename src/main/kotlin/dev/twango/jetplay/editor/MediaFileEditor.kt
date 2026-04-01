package dev.twango.jetplay.editor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

class MediaFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "ogv")
        private val log = Logger.getInstance(MediaFileEditor::class.java)

        private val playerHtml: String by lazy {
            MediaFileEditor::class.java.getResource("/player/index.html")?.readText()
                ?: error("Player UI not found — run 'npm run build' in the ui/ directory")
        }
    }

    private val isVideo = file.extension?.lowercase() in VIDEO_EXTENSIONS
    private val browser = JBCefBrowser()
    @Volatile private var disposed = false
    private var transcodeThread: Thread? = null

    private val component: JComponent = JPanel(BorderLayout()).apply {
        add(browser.component, BorderLayout.CENTER)
    }

    init {
        val extension = file.extension?.lowercase()
        if (MediaTranscoder.needsTranscoding(extension)) {
            val reason = "${extension?.uppercase()} uses codecs not natively supported by the embedded browser. Converting to WebM (VP9/Opus) for playback."
            loadPlayer(state = "loading", transcodingReason = reason)
            transcodeThread = thread(name = "jetplay-transcode", isDaemon = true) {
                try {
                    val transcoded = MediaTranscoder.transcode(file.toNioPath().toFile()) { percent ->
                        if (!disposed) {
                            SwingUtilities.invokeLater {
                                if (!disposed) {
                                    browser.cefBrowser.executeJavaScript(
                                        "window.jetplayUpdateProgress?.($percent)", "", 0
                                    )
                                }
                            }
                        }
                    }
                    val url = transcoded.toURI().toString()
                    if (!disposed) {
                        SwingUtilities.invokeLater {
                            if (!disposed) {
                                browser.cefBrowser.executeJavaScript(
                                    "window.jetplayReady?.('${escapeJs(url)}')", "", 0
                                )
                            }
                        }
                    }
                } catch (e: InterruptedException) {
                    log.info("Transcoding interrupted for ${file.name}")
                } catch (e: Exception) {
                    log.warn("Transcoding failed for ${file.name}", e)
                    if (!disposed) {
                        SwingUtilities.invokeLater {
                            if (!disposed) {
                                browser.cefBrowser.executeJavaScript(
                                    "window.jetplayError?.('${escapeJs(e.message ?: "Unknown error")}')", "", 0
                                )
                            }
                        }
                    }
                }
            }
        } else {
            loadPlayer(mediaUrl = file.toNioPath().toUri().toString())
        }
    }

    private fun loadPlayer(
        mediaUrl: String? = null,
        state: String = "ready",
        errorMessage: String = "",
        transcodingReason: String = ""
    ) {
        val config = buildString {
            append("<script>window.jetplay = {")
            append("state: '${state}',")
            append("isVideo: ${isVideo},")
            append("fileName: '${escapeJs(file.name)}',")
            append("fileExtension: '${escapeJs(file.extension ?: "")}',")
            if (mediaUrl != null) append("mediaUrl: '${escapeJs(mediaUrl)}',")
            if (errorMessage.isNotEmpty()) append("errorMessage: '${escapeJs(errorMessage)}',")
            if (transcodingReason.isNotEmpty()) append("transcodingReason: '${escapeJs(transcodingReason)}',")
            append("};</script>")
        }

        val html = playerHtml.replace("</head>", "$config</head>")
        browser.loadHTML(html)
    }

    private fun escapeJs(s: String): String =
        s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("<", "\\x3c")
            .replace(">", "\\x3e")

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
        disposed = true
        transcodeThread?.interrupt()
        browser.dispose()
    }
}
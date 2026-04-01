package dev.twango.jetplay.editor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.*
import kotlin.concurrent.thread

class MediaFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "ogv")
        private val log = Logger.getInstance(MediaFileEditor::class.java)
    }

    private val isVideo = file.extension?.lowercase() in VIDEO_EXTENSIONS
    private val browser = JBCefBrowser()

    private val component: JComponent = JPanel(BorderLayout()).apply {
        add(browser.component, BorderLayout.CENTER)
    }

    init {
        val extension = file.extension?.lowercase()
        if (MediaTranscoder.needsTranscoding(extension)) {
            loadPlayerHtml(null, isVideo, loading = true)
            thread(name = "jetplay-transcode", isDaemon = true) {
                try {
                    val transcoded = MediaTranscoder.transcode(file.toNioPath().toFile())
                    val url = transcoded.toURI().toString()
                    SwingUtilities.invokeLater { loadPlayerHtml(url, isVideo) }
                } catch (e: Exception) {
                    log.warn("Transcoding failed for ${file.name}", e)
                    SwingUtilities.invokeLater { loadPlayerHtml(null, isVideo, error = e.message) }
                }
            }
        } else {
            val url = file.toNioPath().toUri().toString()
            loadPlayerHtml(url, isVideo)
        }
    }

    private fun loadPlayerHtml(mediaUrl: String?, isVideo: Boolean, loading: Boolean = false, error: String? = null) {
        val fileName = file.name.replace("\"", "&quot;").replace("<", "&lt;").replace("&", "&amp;")
        val safeError = error?.replace("\"", "&quot;")?.replace("<", "&lt;")?.replace("&", "&amp;") ?: ""

        val html = """
<!DOCTYPE html>
<html>
<head>
<style>
    :root {
        --bg: #2b2b2b;
        --bg-elevated: #313335;
        --bg-hover: #3c3f41;
        --text: #bababa;
        --text-muted: #787878;
        --accent: #4a88c7;
        --accent-hover: #5a9bd5;
        --error: #e05555;
        --border: #404040;
        --controls-bg: #1e1e1e;
    }
    @media (prefers-color-scheme: light) {
        :root {
            --bg: #f5f5f5;
            --bg-elevated: #ffffff;
            --bg-hover: #e8e8e8;
            --text: #2b2b2b;
            --text-muted: #888888;
            --accent: #2675bf;
            --accent-hover: #3585cf;
            --error: #c44040;
            --border: #d0d0d0;
            --controls-bg: #e8e8e8;
        }
    }
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body {
        width: 100%; height: 100%; overflow: hidden;
        background: var(--bg); color: var(--text);
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
        -webkit-font-smoothing: antialiased;
    }

    /* Shared layout */
    .container { display: flex; flex-direction: column; height: 100%; }

    /* --- VIDEO PLAYER --- */
    .video-wrapper {
        flex: 1; display: flex; align-items: center; justify-content: center;
        background: #000; min-height: 0; overflow: hidden;
    }
    video {
        max-width: 100%; max-height: 100%; outline: none;
    }
    video::-webkit-media-controls-panel { background: linear-gradient(transparent, rgba(0,0,0,0.7)); }

    /* --- AUDIO PLAYER --- */
    .audio-display {
        flex: 1; display: flex; flex-direction: column;
        align-items: center; justify-content: center; gap: 24px;
        background: var(--bg); user-select: none;
    }
    .audio-icon {
        width: 80px; height: 80px; border-radius: 20px;
        background: var(--bg-elevated); border: 1px solid var(--border);
        display: flex; align-items: center; justify-content: center;
        font-size: 36px; color: var(--accent);
        box-shadow: 0 2px 8px rgba(0,0,0,0.15);
    }
    .audio-filename {
        font-size: 15px; font-weight: 500; color: var(--text);
        max-width: 80%; text-align: center; word-break: break-word;
    }
    .audio-ext {
        display: inline-block; font-size: 11px; font-weight: 600; letter-spacing: 0.5px;
        color: var(--accent); background: rgba(74,136,199,0.12);
        padding: 3px 8px; border-radius: 4px; margin-top: 4px; text-transform: uppercase;
    }
    .audio-controls {
        width: 100%; max-width: 500px; padding: 0 24px 24px;
    }
    audio {
        width: 100%; outline: none; height: 40px;
        filter: sepia(20%) saturate(70%) grayscale(1) contrast(99%) invert(12%);
    }
    audio::-webkit-media-controls-panel { background: var(--controls-bg); border-radius: 8px; }

    /* --- LOADING STATE --- */
    .loading-state {
        flex: 1; display: flex; flex-direction: column;
        align-items: center; justify-content: center; gap: 20px;
    }
    .spinner {
        width: 32px; height: 32px; border-radius: 50%;
        border: 3px solid var(--border);
        border-top-color: var(--accent);
        animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .loading-text { font-size: 13px; color: var(--text-muted); }
    .loading-filename { font-size: 12px; color: var(--text-muted); opacity: 0.7; margin-top: -8px; }

    /* --- ERROR STATE --- */
    .error-state {
        flex: 1; display: flex; flex-direction: column;
        align-items: center; justify-content: center; gap: 12px;
    }
    .error-icon { font-size: 32px; opacity: 0.8; }
    .error-title { font-size: 14px; font-weight: 500; color: var(--error); }
    .error-detail {
        font-size: 12px; color: var(--text-muted);
        max-width: 400px; text-align: center; word-break: break-word;
    }
</style>
</head>
<body>
<div class="container">
${when {
    loading -> """
    <div class="loading-state">
        <div class="spinner"></div>
        <div class="loading-text">Transcoding for playback...</div>
        <div class="loading-filename">$fileName</div>
    </div>"""

    error != null -> """
    <div class="error-state">
        <div class="error-icon">&#9888;</div>
        <div class="error-title">Unable to play this file</div>
        <div class="error-detail">$safeError</div>
    </div>"""

    mediaUrl != null && isVideo -> """
    <div class="video-wrapper">
        <video controls autoplay src="$mediaUrl"></video>
    </div>"""

    mediaUrl != null -> """
    <div class="audio-display">
        <div class="audio-icon">&#9835;</div>
        <div>
            <div class="audio-filename">$fileName</div>
            <div style="text-align:center"><span class="audio-ext">${file.extension ?: ""}</span></div>
        </div>
        <div class="audio-controls">
            <audio controls autoplay src="$mediaUrl"></audio>
        </div>
    </div>"""

    else -> ""
}}
</div>
</body>
</html>"""
        browser.loadHTML(html)
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
        browser.dispose()
    }
}
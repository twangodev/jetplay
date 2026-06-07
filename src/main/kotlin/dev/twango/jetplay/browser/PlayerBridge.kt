package dev.twango.jetplay.browser

import com.intellij.ide.BrowserUtil
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import dev.twango.jetplay.transcode.MediaInfo
import javax.swing.SwingUtilities

class PlayerBridge(private val browser: JBCefBrowser) {

    @Volatile
    var disposed = false
        private set

    val openLinkQuery: JBCefJSQuery = JBCefJSQuery.create(browser as JBCefBrowserBase).apply {
        addHandler { url ->
            BrowserUtil.browse(url)
            null
        }
    }

    fun executeJs(js: String) {
        if (!disposed) {
            SwingUtilities.invokeLater {
                if (!disposed) {
                    browser.cefBrowser.executeJavaScript(js, "", 0)
                }
            }
        }
    }

    fun updateProgress(percent: Double) = executeJs("window.jetplayUpdateProgress?.($percent)")

    fun updateDownloadProgress(percent: Double) = executeJs("window.jetplayUpdateDownloadProgress?.($percent)")

    fun mediaReady(url: String) = executeJs("window.jetplayReady?.('${escapeJs(url)}')")

    fun showError(message: String) = executeJs("window.jetplayError?.('${escapeJs(message)}')")

    // Stash the bars as well as calling the handler: extraction can finish
    // before the page defines window.jetplayWaveform (short files), so the app
    // reads window.__jetplayWaveform on mount to avoid dropping an early push.
    fun sendWaveform(bars: List<Double>) =
        executeJs(
            "window.__jetplayWaveform=[${bars.joinToString(",")}];" +
                "if(window.jetplayWaveform)window.jetplayWaveform(window.__jetplayWaveform)",
        )

    // Same stash-then-call pattern as sendWaveform: the probe can finish before
    // the page defines window.jetplayMediaInfo, so the app reads
    // window.__jetplayMediaInfo on mount to avoid dropping an early push.
    fun sendMediaInfo(info: MediaInfo) {
        val json = mediaInfoJson(info) ?: return
        executeJs("window.__jetplayMediaInfo=$json;if(window.jetplayMediaInfo)window.jetplayMediaInfo(window.__jetplayMediaInfo)")
    }

    fun loadHtml(html: String) = browser.loadHTML(html)

    fun dispose() {
        disposed = true
        openLinkQuery.dispose()
        browser.dispose()
    }

    companion object {
        fun escapeJs(s: String): String = s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("<", "\\x3c")
            .replace(">", "\\x3e")

        // The media-info payload carries arbitrary tag text and a base64 art URL,
        // so it's built as strict JSON (a subset of JS) rather than spliced.
        // Returns null when there's nothing to send.
        internal fun mediaInfoJson(info: MediaInfo): String? {
            val parts = buildList {
                info.codec?.let { add("\"codec\":${jsonString(it)}") }
                info.container?.let { add("\"container\":${jsonString(it)}") }
                info.sampleRateHz?.let { add("\"sampleRateHz\":$it") }
                info.channels?.let { add("\"channels\":$it") }
                info.channelLabel?.let { add("\"channelLabel\":${jsonString(it)}") }
                info.bitDepth?.let { add("\"bitDepth\":${jsonString(it)}") }
                info.bitrateBps?.let { add("\"bitrateBps\":$it") }
                info.durationMs?.let { add("\"durationMs\":$it") }
                info.sizeBytes?.let { add("\"sizeBytes\":$it") }
                info.width?.let { add("\"width\":$it") }
                info.height?.let { add("\"height\":$it") }
                info.frameRate?.let { add("\"frameRate\":$it") }
                info.videoCodec?.let { add("\"videoCodec\":${jsonString(it)}") }
                info.pixelFormat?.let { add("\"pixelFormat\":${jsonString(it)}") }
                info.videoBitrateBps?.let { add("\"videoBitrateBps\":$it") }
                if (info.tags.isNotEmpty()) {
                    val arr = info.tags.joinToString(",", "[", "]") { tag ->
                        "{\"label\":${jsonString(tag.label)},\"value\":${jsonString(tag.value)}}"
                    }
                    add("\"tags\":$arr")
                }
                info.albumArt?.let { add("\"albumArt\":${jsonString(it)}") }
            }
            if (parts.isEmpty()) return null
            return parts.joinToString(",", "{", "}")
        }

        internal fun jsonString(s: String): String {
            val sb = StringBuilder(s.length + 2)
            sb.append('"')
            for (c in s) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    // Valid in JSON but terminate a JS string literal — must escape.
                    '\u2028' -> sb.append("\\u2028")
                    '\u2029' -> sb.append("\\u2029")
                    else -> if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
                }
            }
            sb.append('"')
            return sb.toString()
        }
    }
}

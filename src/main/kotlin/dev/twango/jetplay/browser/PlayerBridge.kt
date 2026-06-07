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
        val fields = buildList {
            info.codec?.let { add("codec:'${escapeJs(it)}'") }
            info.container?.let { add("container:'${escapeJs(it)}'") }
            info.sampleRateHz?.let { add("sampleRateHz:$it") }
            info.channels?.let { add("channels:$it") }
            info.channelLabel?.let { add("channelLabel:'${escapeJs(it)}'") }
            info.bitDepth?.let { add("bitDepth:'${escapeJs(it)}'") }
            info.bitrateBps?.let { add("bitrateBps:$it") }
            info.durationMs?.let { add("durationMs:$it") }
            info.sizeBytes?.let { add("sizeBytes:$it") }
        }
        if (fields.isEmpty()) return
        val obj = "{${fields.joinToString(",")}}"
        executeJs("window.__jetplayMediaInfo=$obj;if(window.jetplayMediaInfo)window.jetplayMediaInfo(window.__jetplayMediaInfo)")
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
    }
}

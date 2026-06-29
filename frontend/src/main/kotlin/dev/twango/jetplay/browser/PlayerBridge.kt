package dev.twango.jetplay.browser

import com.intellij.ide.BrowserUtil
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import dev.twango.jetplay.media.MediaInfo
import dev.twango.jetplay.media.Spectrogram
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.SwingUtilities

class PlayerBridge(private val browser: JBCefBrowser) {

    @Volatile
    var disposed = false
        private set

    // JCEF drops executeJavaScript until the page finishes loading, so queue calls and flush them on load-end.
    private var pageLoaded = false
    private val pendingJs = mutableListOf<String>()

    val openLinkQuery: JBCefJSQuery = JBCefJSQuery.create(browser as JBCefBrowserBase).apply {
        addHandler { url ->
            BrowserUtil.browse(url)
            null
        }
    }

    // The spectrogram is heavy, so the page asks for it only when the user first reveals that view.
    @Volatile
    var onSpectrogramRequest: (() -> Unit)? = null

    val spectrogramRequestQuery: JBCefJSQuery = JBCefJSQuery.create(browser as JBCefBrowserBase).apply {
        addHandler { _ ->
            onSpectrogramRequest?.invoke()
            null
        }
    }

    init {
        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(b: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain != true) return
                    val queued = synchronized(pendingJs) {
                        pageLoaded = true
                        pendingJs.toList().also { pendingJs.clear() }
                    }
                    queued.forEach(::runJs)
                }
            },
            browser.cefBrowser,
        )
    }

    fun executeJs(js: String) {
        if (disposed) return
        val runNow = synchronized(pendingJs) {
            if (pageLoaded) {
                true
            } else {
                pendingJs.add(js)
                false
            }
        }
        if (runNow) runJs(js)
    }

    private fun runJs(js: String) {
        SwingUtilities.invokeLater {
            if (!disposed) browser.cefBrowser.executeJavaScript(js, "", 0)
        }
    }

    fun isShowing(): Boolean = !disposed && browser.component.isShowing

    // Stash before notifying: a fast transcode can beat page load.
    fun updateProgress(percent: Double) =
        executeJs("window.__jetplayProgress=$percent;window.jetplayUpdateProgress?.($percent)")

    fun mediaReady(url: String) =
        executeJs("window.__jetplayReadyUrl='${PlayerPayloads.escapeJs(url)}';window.jetplayReady?.('${PlayerPayloads.escapeJs(url)}')")

    fun showError(message: String) =
        executeJs("window.__jetplayError='${PlayerPayloads.escapeJs(message)}';window.jetplayError?.('${PlayerPayloads.escapeJs(message)}')")

    // Same stash-then-notify race guard as updateProgress.
    fun sendWaveform(bars: List<Double>) = executeJs(
        "window.__jetplayWaveform=[${bars.joinToString(",")}];" +
            "if(window.jetplayWaveform)window.jetplayWaveform(window.__jetplayWaveform)",
    )

    fun sendMediaInfo(info: MediaInfo) {
        val json = PlayerPayloads.mediaInfoJson(info) ?: return
        executeJs("window.__jetplayMediaInfo=$json;if(window.jetplayMediaInfo)window.jetplayMediaInfo(window.__jetplayMediaInfo)")
    }

    // Carries either the matrix or {ok:false} so a lazy request that finds nothing can stop the spinner.
    fun sendSpectrogram(spec: Spectrogram?) {
        val json = PlayerPayloads.spectrogramJson(spec)
        executeJs("window.__jetplaySpectrogram=$json;if(window.jetplaySpectrogram)window.jetplaySpectrogram(window.__jetplaySpectrogram)")
    }

    fun loadHtml(html: String) {
        synchronized(pendingJs) {
            pageLoaded = false
            pendingJs.clear()
        }
        // JCEF/Swing access must be on the EDT; coroutine error paths can reach here off-thread.
        SwingUtilities.invokeLater {
            if (!disposed) browser.loadHTML(html)
        }
    }

    fun dispose() {
        disposed = true
        openLinkQuery.dispose()
        spectrogramRequestQuery.dispose()
        browser.dispose()
    }
}

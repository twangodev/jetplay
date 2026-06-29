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
import java.util.Base64
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
        executeJs("window.__jetplayReadyUrl='${escapeJs(url)}';window.jetplayReady?.('${escapeJs(url)}')")

    fun showError(message: String) =
        executeJs("window.__jetplayError='${escapeJs(message)}';window.jetplayError?.('${escapeJs(message)}')")

    // Same stash-then-notify race guard as updateProgress.
    fun sendWaveform(bars: List<Double>) = executeJs(
        "window.__jetplayWaveform=[${bars.joinToString(",")}];" +
            "if(window.jetplayWaveform)window.jetplayWaveform(window.__jetplayWaveform)",
    )

    fun sendMediaInfo(info: MediaInfo) {
        val json = mediaInfoJson(info) ?: return
        executeJs("window.__jetplayMediaInfo=$json;if(window.jetplayMediaInfo)window.jetplayMediaInfo(window.__jetplayMediaInfo)")
    }

    // Carries either the matrix or {ok:false} so a lazy request that finds nothing can stop the spinner.
    fun sendSpectrogram(spec: Spectrogram?) {
        val json = spectrogramJson(spec)
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

    companion object {
        private const val HEX_RADIX = 16
        private const val UNICODE_ESCAPE_HEX_LENGTH = 4

        fun escapeJs(s: String): String = s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("<", "\\x3c")
            .replace(">", "\\x3e")

        // Strict JSON, not spliced: it carries arbitrary tag text and a base64 art URL.
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

        // Numbers are emitted as literals; only the base64 matrix is a string (it never contains JS specials).
        internal fun spectrogramJson(spec: Spectrogram?): String {
            if (spec == null) return "{\"ok\":false}"
            val data = Base64.getEncoder().encodeToString(spec.magnitudes)
            return buildString {
                append("{\"ok\":true")
                append(",\"timeCols\":${spec.timeCols}")
                append(",\"freqBins\":${spec.freqBins}")
                append(",\"durationMs\":${spec.durationMs}")
                append(",\"sampleRateHz\":${spec.sampleRateHz}")
                append(",\"minHz\":${spec.minHz}")
                append(",\"maxHz\":${spec.maxHz}")
                append(",\"dbFloor\":${spec.dbFloor}")
                append(",\"dbCeil\":${spec.dbCeil}")
                append(",\"logFreq\":${spec.logFreq}")
                append(",\"data\":${jsonString(data)}")
                append("}")
            }
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

                    // Valid in JSON but terminate a JS string literal.
                    '\u2028' -> sb.append("\\u2028")

                    '\u2029' -> sb.append("\\u2029")

                    else -> if (c < ' ') {
                        sb.append("\\u").append(c.code.toString(HEX_RADIX).padStart(UNICODE_ESCAPE_HEX_LENGTH, '0'))
                    } else {
                        sb.append(c)
                    }
                }
            }
            sb.append('"')
            return sb.toString()
        }
    }
}

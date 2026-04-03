package dev.twango.jetplay.browser

import com.intellij.ide.BrowserUtil
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
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

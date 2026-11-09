package dev.twango.jetplay.browser

class PlayerHtmlLoader(private val bridge: PlayerBridge) {

    companion object {
        private val playerHtml: String by lazy {
            PlayerHtmlLoader::class.java.getResource("/player/index.html")?.readText()
                ?: error("Player UI not found — run 'npm run build' in the ui/ directory")
        }

        internal fun buildConfigScript(
            config: PlayerConfig,
            openLinkJs: String,
            spectrogramRequestJs: String = "",
            copyTextJs: String = "",
            savePlaybackJs: String = "",
        ): String = buildString {
            append("<script>")
            append("window.jetplayOpenLink = function(url) { $openLinkJs };")
            append("window.jetplayRequestSpectrogram = function() { $spectrogramRequestJs };")
            if (copyTextJs.isNotEmpty()) {
                append("window.jetplayCopyText = function(text) { return new Promise(function(resolve, reject) { $copyTextJs }); };")
            }
            if (savePlaybackJs.isNotEmpty()) {
                append("window.jetplaySavePlayback = function(update) { return new Promise(function(resolve, reject) { ")
                append("$savePlaybackJs }); };")
            }
            append("window.jetplay = ${PlayerPayloads.configJson(config)};</script>")
        }
    }

    fun load(config: PlayerConfig) {
        val openLinkJs = bridge.openLinkQuery.inject("url")
        val spectrogramRequestJs = bridge.spectrogramRequestQuery.inject("''")
        val copyTextJs = bridge.copyTextQuery.inject(
            "text",
            "function() { resolve(); }",
            "function(code, message) { reject(new Error(message)); }",
        )
        val savePlaybackJs = bridge.savePlaybackQuery.inject(
            "JSON.stringify(update)",
            "function() { resolve(); }",
            "function(code, message) { reject(new Error(message)); }",
        )
        val configScript = buildConfigScript(config, openLinkJs, spectrogramRequestJs, copyTextJs, savePlaybackJs)
        bridge.loadHtml(playerHtml.replace("</head>", "$configScript</head>"))
    }
}

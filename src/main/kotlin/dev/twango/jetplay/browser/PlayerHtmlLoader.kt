package dev.twango.jetplay.browser

class PlayerHtmlLoader(private val bridge: PlayerBridge) {

    companion object {
        private val playerHtml: String by lazy {
            PlayerHtmlLoader::class.java.getResource("/player/index.html")?.readText()
                ?: error("Player UI not found — run 'npm run build' in the ui/ directory")
        }
    }

    fun load(config: PlayerConfig) {
        val openLinkJs = bridge.openLinkQuery.inject("url")
        val configScript = buildConfigScript(config, openLinkJs)
        bridge.loadHtml(playerHtml.replace("</head>", "$configScript</head>"))
    }

    private fun buildConfigScript(config: PlayerConfig, openLinkJs: String): String =
        buildString {
            append("<script>")
            append("window.jetplayOpenLink = function(url) { $openLinkJs };")
            append("window.jetplay = {")
            append("state: '${config.state}',")
            append("isVideo: ${config.isVideo},")
            append("fileName: '${PlayerBridge.escapeJs(config.fileName)}',")
            append("fileExtension: '${PlayerBridge.escapeJs(config.fileExtension)}',")
            config.mediaUrl?.let { append("mediaUrl: '${PlayerBridge.escapeJs(it)}',") }
            if (config.errorMessage.isNotEmpty()) append("errorMessage: '${PlayerBridge.escapeJs(config.errorMessage)}',")
            if (config.transcodingReason.isNotEmpty()) append("transcodingReason: '${PlayerBridge.escapeJs(config.transcodingReason)}',")
            append("};</script>")
        }
}

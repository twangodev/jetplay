package dev.twango.jetplay.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerHtmlLoaderTest {

    private fun buildScript(config: PlayerConfig, openLinkJs: String = ""): String =
        PlayerHtmlLoader.buildConfigScript(config, openLinkJs)

    @Test
    fun wrapsInScriptTags() {
        val result = buildScript(PlayerConfig())
        assertTrue(result.startsWith("<script>"))
        assertTrue(result.endsWith("</script>"))
    }

    @Test
    fun containsState() {
        val result = buildScript(PlayerConfig(state = "loading"))
        assertTrue(result.contains("state: 'loading'"))
    }

    @Test
    fun containsFileName() {
        val result = buildScript(PlayerConfig(fileName = "my-track"))
        assertTrue(result.contains("fileName: 'my-track'"))
    }

    @Test
    fun containsIsVideo() {
        val resultTrue = buildScript(PlayerConfig(isVideo = true))
        assertTrue(resultTrue.contains("isVideo: true"))

        val resultFalse = buildScript(PlayerConfig(isVideo = false))
        assertTrue(resultFalse.contains("isVideo: false"))
    }

    @Test
    fun includesMediaUrlWhenPresent() {
        val result = buildScript(PlayerConfig(mediaUrl = "file:///test.webm"))
        assertTrue(result.contains("mediaUrl: 'file:///test.webm'"))
    }

    @Test
    fun omitsMediaUrlWhenNull() {
        val result = buildScript(PlayerConfig(mediaUrl = null))
        assertFalse(result.contains("mediaUrl:"))
    }

    @Test
    fun escapesSpecialCharsInFileName() {
        val result = buildScript(PlayerConfig(fileName = "it's <a> \"test\""))
        assertTrue(result.contains("fileName: 'it\\'s \\x3ca\\x3e \\\"test\\\"'"))
    }

    @Test
    fun includesErrorMessageWhenNotEmpty() {
        val result = buildScript(PlayerConfig(errorMessage = "Something broke"))
        assertTrue(result.contains("errorMessage: 'Something broke'"))
    }

    @Test
    fun omitsErrorMessageWhenEmpty() {
        val result = buildScript(PlayerConfig(errorMessage = ""))
        assertFalse(result.contains("errorMessage:"))
    }

    @Test
    fun includesUiStrings() {
        val result = buildScript(
            PlayerConfig(
                ui = UiStrings(
                    downloadingLabel = "Loading...",
                    transcodingLabel = "Converting...",
                    transcodingTip = "Use webm",
                    errorTitle = "Error!",
                ),
            ),
        )
        assertTrue(result.contains("downloadingLabel: 'Loading...'"))
        assertTrue(result.contains("transcodingLabel: 'Converting...'"))
        assertTrue(result.contains("transcodingTip: 'Use webm'"))
        assertTrue(result.contains("errorTitle: 'Error!'"))
    }

    @Test
    fun includesOpenLinkJs() {
        val result = buildScript(PlayerConfig(), openLinkJs = "console.log(url)")
        assertTrue(result.contains("window.jetplayOpenLink = function(url) { console.log(url) }"))
    }
}

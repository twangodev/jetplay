package dev.twango.jetplay.browser

import dev.twango.jetplay.playback.PlaybackMemorySnapshot
import dev.twango.jetplay.playback.PlaybackPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerHtmlLoaderTest {

    private fun buildScript(config: PlayerConfig, openLinkJs: String = "", spectrogramRequestJs: String = ""): String =
        PlayerHtmlLoader.buildConfigScript(config, openLinkJs, spectrogramRequestJs)

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
    fun preservesOriginalReferencePathSeparatelyFromPlaybackUrl() {
        val result = buildScript(
            PlayerConfig(filePath = "assets/it's <demo>.mp4", mediaUrl = "http://127.0.0.1:1234/temporary.webm"),
        )
        assertTrue(result.contains("filePath: 'assets/it\\'s \\x3cdemo\\x3e.mp4'"))
        assertTrue(result.contains("mediaUrl: 'http://127.0.0.1:1234/temporary.webm'"))
    }

    @Test
    fun clipboardFunctionWaitsForNativeCallback() {
        val result = PlayerHtmlLoader.buildConfigScript(PlayerConfig(), "", copyTextJs = "nativeCopy(text, resolve, reject)")
        assertTrue(result.contains("window.jetplayCopyText = function(text) { return new Promise(function(resolve, reject) {"))
        assertTrue(result.contains("nativeCopy(text, resolve, reject)"))
    }

    @Test
    fun omitsClipboardFunctionWithoutNativeHandler() {
        assertFalse(buildScript(PlayerConfig()).contains("jetplayCopyText"))
    }

    @Test
    fun includesStoredPlaybackMemory() {
        val result = buildScript(
            PlayerConfig(playbackMemory = PlaybackMemorySnapshot(PlaybackPreferences(0.3, true, 1.5), 12.45)),
        )
        assertTrue(result.contains("\"volume\":0.3"))
        assertTrue(result.contains("\"muted\":true"))
        assertTrue(result.contains("\"playbackRate\":1.5"))
        assertTrue(result.contains("\"positionSeconds\":12.45"))
    }

    @Test
    fun persistenceFunctionWaitsForNativeCallback() {
        val result = PlayerHtmlLoader.buildConfigScript(PlayerConfig(), "", savePlaybackJs = "nativeSave(update, resolve, reject)")
        assertTrue(result.contains("window.jetplaySavePlayback = function(update) { return new Promise(function(resolve, reject) {"))
        assertTrue(result.contains("nativeSave(update, resolve, reject)"))
    }

    @Test
    fun omitsPersistenceFunctionWithoutNativeHandler() {
        assertFalse(buildScript(PlayerConfig()).contains("jetplaySavePlayback"))
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
    fun includesTranscodingReasonWhenNotEmpty() {
        val result = buildScript(PlayerConfig(transcodingReason = "AAC needs conversion"))
        assertTrue(result.contains("transcodingReason: 'AAC needs conversion'"))
    }

    @Test
    fun omitsTranscodingReasonWhenEmpty() {
        val result = buildScript(PlayerConfig(transcodingReason = ""))
        assertFalse(result.contains("transcodingReason:"))
    }

    @Test
    fun includesUiStrings() {
        val result = buildScript(
            PlayerConfig(
                ui = UiStrings(
                    transcodingLabel = "Converting...",
                    transcodingTip = "Use webm",
                    errorTitle = "Error!",
                ),
            ),
        )
        assertTrue(result.contains("transcodingLabel: 'Converting...'"))
        assertTrue(result.contains("transcodingTip: 'Use webm'"))
        assertTrue(result.contains("errorTitle: 'Error!'"))
    }

    @Test
    fun includesOpenLinkJs() {
        val result = buildScript(PlayerConfig(), openLinkJs = "console.log(url)")
        assertTrue(result.contains("window.jetplayOpenLink = function(url) { console.log(url) }"))
    }

    @Test
    fun includesSpectrogramRequestJs() {
        val result = buildScript(PlayerConfig(), spectrogramRequestJs = "requestIt()")
        assertTrue(result.contains("window.jetplayRequestSpectrogram = function() { requestIt() }"))
    }
}

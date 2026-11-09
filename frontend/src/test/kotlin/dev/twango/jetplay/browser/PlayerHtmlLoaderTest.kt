package dev.twango.jetplay.browser

import dev.twango.jetplay.playback.PlaybackMemorySnapshot
import dev.twango.jetplay.playback.PlaybackPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerHtmlLoaderTest {
    private fun configJson(script: String): String = script.substringAfter("window.jetplay = ").substringBeforeLast(";</script>")

    private fun roundTrip(config: PlayerConfig): PlayerConfig = Json.decodeFromString(
        configJson(PlayerHtmlLoader.buildConfigScript(config, "")),
    )

    @Test
    fun wrapsConfigurationInOneScriptElement() {
        val script = PlayerHtmlLoader.buildConfigScript(PlayerConfig(), "")
        assertTrue(script.startsWith("<script>"))
        assertTrue(script.endsWith("</script>"))
        assertEquals(PlayerConfig(), roundTrip(PlayerConfig()))
    }

    @Test
    fun defaultSnapshotEmitsCompleteContract() {
        val config = Json.parseToJsonElement(configJson(PlayerHtmlLoader.buildConfigScript(PlayerConfig(), ""))).jsonObject
        val snapshot = config.getValue("playbackMemory").jsonObject
        val preferences = snapshot.getValue("preferences").jsonObject
        assertEquals(1.0, preferences.getValue("volume").jsonPrimitive.double, 0.0)
        assertEquals("false", preferences.getValue("muted").jsonPrimitive.content)
        assertEquals(1.0, preferences.getValue("playbackRate").jsonPrimitive.double, 0.0)
        assertEquals(0.0, snapshot.getValue("positionSeconds").jsonPrimitive.double, 0.0)
    }

    @Test
    fun sourceIdentityAndPlaybackUrlRemainSeparateAndLossless() {
        val config = PlayerConfig(
            state = "loading",
            isVideo = true,
            fileName = "it's <a> \"test\"\r.mp4",
            filePath = "assets/it's <demo>\r.mp4",
            fileExtension = "mp4",
            mediaUrl = "http://127.0.0.1:1234/temporary.webm",
            errorMessage = "Error\r\n</script>",
            transcodingReason = "AAC needs conversion",
        )
        assertEquals(config, roundTrip(config))
    }

    @Test
    fun inlineConfigurationCannotCloseTheScriptElement() {
        val config = PlayerConfig(fileName = "</script><script>alert('filename')</script>")
        val script = PlayerHtmlLoader.buildConfigScript(config, "")
        assertFalse(configJson(script).contains('<'))
        assertEquals(1, script.split("</script>").size - 1)
        assertEquals(config, roundTrip(config))
    }

    @Test
    fun optionalCopyIsOmittedSoBrowserDefaultsStillApply() {
        val config = PlayerConfig(errorMessage = "", transcodingReason = "", ui = UiStrings("", "", ""))
        val payload = Json.parseToJsonElement(configJson(PlayerHtmlLoader.buildConfigScript(config, ""))).jsonObject
        assertFalse(payload.containsKey("mediaUrl"))
        assertFalse(payload.containsKey("errorMessage"))
        assertFalse(payload.containsKey("transcodingReason"))
        assertTrue(payload.getValue("ui").jsonObject.isEmpty())
    }

    @Test
    fun translatedCopyAndStoredPlaybackMemoryRoundTrip() {
        val config = PlayerConfig(
            ui = UiStrings("Converting...", "Use webm", "Error!"),
            playbackMemory = PlaybackMemorySnapshot(PlaybackPreferences(0.3, true, 1.5), 12.45),
        )
        assertEquals(config, roundTrip(config))
    }

    @Test
    fun nativeClipboardAndPersistenceFunctionsWaitForTheirCallbacks() {
        val script = PlayerHtmlLoader.buildConfigScript(
            PlayerConfig(),
            "",
            copyTextJs = "nativeCopy(text, resolve, reject)",
            savePlaybackJs = "nativeSave(update, resolve, reject)",
        )
        assertTrue(script.contains("window.jetplayCopyText = function(text) { return new Promise(function(resolve, reject) {"))
        assertTrue(script.contains("nativeCopy(text, resolve, reject)"))
        assertTrue(script.contains("window.jetplaySavePlayback = function(update) { return new Promise(function(resolve, reject) {"))
        assertTrue(script.contains("nativeSave(update, resolve, reject)"))
    }

    @Test
    fun browserPreviewDoesNotGetSuccessfulNoOpNativeStubs() {
        val script = PlayerHtmlLoader.buildConfigScript(PlayerConfig(), "")
        assertFalse(script.contains("jetplayCopyText"))
        assertFalse(script.contains("jetplaySavePlayback"))
    }

    @Test
    fun includesOpenLinkAndSpectrogramCallbacks() {
        val script = PlayerHtmlLoader.buildConfigScript(PlayerConfig(), "console.log(url)", "requestIt()")
        assertTrue(script.contains("window.jetplayOpenLink = function(url) { console.log(url) }"))
        assertTrue(script.contains("window.jetplayRequestSpectrogram = function() { requestIt() }"))
    }
}

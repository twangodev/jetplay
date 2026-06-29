package dev.twango.jetplay.browser

import dev.twango.jetplay.media.MediaInfo
import dev.twango.jetplay.media.MediaTag
import dev.twango.jetplay.media.Spectrogram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBridgeEscapeTest {

    @Test
    fun plainStringUnchanged() {
        assertEquals("hello world", PlayerBridge.escapeJs("hello world"))
    }

    @Test
    fun backslashEscaped() {
        assertEquals("a\\\\b", PlayerBridge.escapeJs("a\\b"))
    }

    @Test
    fun singleQuoteEscaped() {
        assertEquals("it\\'s", PlayerBridge.escapeJs("it's"))
    }

    @Test
    fun doubleQuoteEscaped() {
        assertEquals("say \\\"hi\\\"", PlayerBridge.escapeJs("say \"hi\""))
    }

    @Test
    fun newlineEscaped() {
        assertEquals("line1\\nline2", PlayerBridge.escapeJs("line1\nline2"))
    }

    @Test
    fun carriageReturnRemoved() {
        assertEquals("ab", PlayerBridge.escapeJs("a\rb"))
    }

    @Test
    fun angleBracketsEscaped() {
        assertEquals("\\x3cscript\\x3e", PlayerBridge.escapeJs("<script>"))
    }

    @Test
    fun combinedSpecialCharacters() {
        val input = "it's a <b>\"test\"</b>\nwith\\stuff\r"
        val expected = "it\\'s a \\x3cb\\x3e\\\"test\\\"\\x3c/b\\x3e\\nwith\\\\stuff"
        assertEquals(expected, PlayerBridge.escapeJs(input))
    }

    @Test
    fun emptyString() {
        assertEquals("", PlayerBridge.escapeJs(""))
    }

    // --- media-info JSON serialization (carries arbitrary tag text) ---

    @Test
    fun jsonStringEscapesQuotesBackslashControlAndLineSeparators() {
        assertEquals("\"a\\\"b\"", PlayerBridge.jsonString("a\"b"))
        assertEquals("\"a\\\\b\"", PlayerBridge.jsonString("a\\b"))
        assertEquals("\"l1\\nl2\"", PlayerBridge.jsonString("l1\nl2"))
        // A lone control char becomes a \u00xx escape.
        assertEquals("\"a\\u0001b\"", PlayerBridge.jsonString("a\u0001b"))
        // U+2028 is legal JSON but terminates a JS string literal, so it must escape.
        assertEquals("\"a\\u2028b\"", PlayerBridge.jsonString("a\u2028b"))
    }

    @Test
    fun mediaInfoJsonEmbedsTagsAndEscapesArbitraryText() {
        val info = MediaInfo(
            codec = "mp3",
            container = "mp3",
            sampleRateHz = 44100,
            channels = 2,
            channelLabel = "stereo",
            bitDepth = null,
            bitrateBps = 320000,
            durationMs = 1000,
            sizeBytes = 40000,
            tags = listOf(MediaTag("Title", "O'Brien \"x\""), MediaTag("Artist", "A\\B")),
            albumArt = "data:image/png;base64,AAAA",
        )
        val json = PlayerBridge.mediaInfoJson(info)!!
        assertTrue(json.startsWith("{") && json.endsWith("}"))
        assertTrue(json.contains("\"sampleRateHz\":44100"))
        assertTrue(json.contains("\"label\":\"Title\",\"value\":\"O'Brien \\\"x\\\"\""))
        assertTrue(json.contains("\"value\":\"A\\\\B\""))
        assertTrue(json.contains("\"albumArt\":\"data:image/png;base64,AAAA\""))
        // Null fields are omitted entirely.
        assertFalse(json.contains("bitDepth"))
    }

    @Test
    fun mediaInfoJsonReturnsNullWhenEverythingIsEmpty() {
        val empty = MediaInfo(null, null, null, null, null, null, null, null, null)
        assertNull(PlayerBridge.mediaInfoJson(empty))
    }

    // --- spectrogram JSON serialization (carries a base64 magnitude matrix) ---

    @Test
    fun spectrogramJsonForNullSignalsUnavailable() {
        assertEquals("{\"ok\":false}", PlayerBridge.spectrogramJson(null))
    }

    @Test
    fun spectrogramJsonEncodesHeaderAndBase64Matrix() {
        val spec = Spectrogram(
            timeCols = 2,
            freqBins = 2,
            durationMs = 1000,
            sampleRateHz = 44100,
            minHz = 20,
            maxHz = 20000,
            dbFloor = -80f,
            dbCeil = 0f,
            logFreq = true,
            magnitudes = byteArrayOf(0, 127, -1, 64),
        )
        val json = PlayerBridge.spectrogramJson(spec)
        assertTrue(json.contains("\"ok\":true"))
        assertTrue(json.contains("\"timeCols\":2"))
        assertTrue(json.contains("\"freqBins\":2"))
        assertTrue(json.contains("\"durationMs\":1000"))
        assertTrue(json.contains("\"sampleRateHz\":44100"))
        assertTrue(json.contains("\"minHz\":20"))
        assertTrue(json.contains("\"maxHz\":20000"))
        assertTrue(json.contains("\"dbFloor\":-80.0"))
        assertTrue(json.contains("\"dbCeil\":0.0"))
        assertTrue(json.contains("\"logFreq\":true"))
        // Base64 of [0x00, 0x7F, 0xFF, 0x40].
        assertTrue(json.contains("\"data\":\"AH//QA==\""))
    }
}

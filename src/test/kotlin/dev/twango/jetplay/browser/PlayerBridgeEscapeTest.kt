package dev.twango.jetplay.browser

import org.junit.Assert.assertEquals
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
}

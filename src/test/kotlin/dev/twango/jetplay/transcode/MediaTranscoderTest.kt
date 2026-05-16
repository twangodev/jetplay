package dev.twango.jetplay.transcode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTranscoderTest {

    @Test
    fun nativeVideoFormatsDoNotNeedTranscoding() {
        assertFalse(MediaTranscoder.needsTranscoding("webm"))
        assertFalse(MediaTranscoder.needsTranscoding("ogv"))
    }

    @Test
    fun nativeAudioFormatsDoNotNeedTranscoding() {
        assertFalse(MediaTranscoder.needsTranscoding("ogg"))
        assertFalse(MediaTranscoder.needsTranscoding("oga"))
        assertFalse(MediaTranscoder.needsTranscoding("opus"))
        assertFalse(MediaTranscoder.needsTranscoding("wav"))
        assertFalse(MediaTranscoder.needsTranscoding("flac"))
        assertFalse(MediaTranscoder.needsTranscoding("mp3"))
    }

    @Test
    fun nonNativeFormatsNeedTranscoding() {
        assertTrue(MediaTranscoder.needsTranscoding("mp4"))
        assertTrue(MediaTranscoder.needsTranscoding("m4v"))
        assertTrue(MediaTranscoder.needsTranscoding("m4a"))
        assertTrue(MediaTranscoder.needsTranscoding("aac"))
    }

    @Test
    fun extensionCheckIsCaseInsensitive() {
        assertFalse(MediaTranscoder.needsTranscoding("WEBM"))
        assertFalse(MediaTranscoder.needsTranscoding("MP3"))
        assertFalse(MediaTranscoder.needsTranscoding("Wav"))
        assertTrue(MediaTranscoder.needsTranscoding("MP4"))
    }

    @Test
    fun nullExtensionNeedsTranscoding() {
        assertTrue(MediaTranscoder.needsTranscoding(null))
    }

    @Test
    fun emptyExtensionNeedsTranscoding() {
        assertTrue(MediaTranscoder.needsTranscoding(""))
    }

    @Test
    fun rawAudioHintsAreRegisteredInPluginXml() {
        val xml = MediaTranscoder::class.java.getResource("/META-INF/plugin.xml")!!.readText()
        val match = Regex("""extensions="([^"]+)"""").find(xml)
            ?: error("Could not find extensions attribute in plugin.xml")
        val registered = match.groupValues[1].split(";").map { it.lowercase() }.toSet()
        val missing = MediaTranscoder.rawAudioExtensions - registered
        assertTrue(
            "RAW_AUDIO_HINTS keys missing from plugin.xml: $missing",
            missing.isEmpty(),
        )
    }
}

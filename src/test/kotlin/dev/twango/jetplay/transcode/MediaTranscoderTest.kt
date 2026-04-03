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
}

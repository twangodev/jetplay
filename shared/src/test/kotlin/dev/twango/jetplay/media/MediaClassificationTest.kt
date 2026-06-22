package dev.twango.jetplay.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaClassificationTest {

    @Test
    fun nativeVideoFormatsDoNotNeedTranscoding() {
        assertFalse(MediaClassification.needsTranscoding("webm"))
        assertFalse(MediaClassification.needsTranscoding("ogv"))
    }

    @Test
    fun nativeAudioFormatsDoNotNeedTranscoding() {
        assertFalse(MediaClassification.needsTranscoding("ogg"))
        assertFalse(MediaClassification.needsTranscoding("oga"))
        assertFalse(MediaClassification.needsTranscoding("opus"))
        assertFalse(MediaClassification.needsTranscoding("wav"))
        assertFalse(MediaClassification.needsTranscoding("flac"))
        assertFalse(MediaClassification.needsTranscoding("mp3"))
    }

    @Test
    fun nonNativeFormatsNeedTranscoding() {
        assertTrue(MediaClassification.needsTranscoding("mp4"))
        assertTrue(MediaClassification.needsTranscoding("m4v"))
        assertTrue(MediaClassification.needsTranscoding("m4a"))
        assertTrue(MediaClassification.needsTranscoding("aac"))
    }

    @Test
    fun transcodingCheckIsCaseInsensitive() {
        assertFalse(MediaClassification.needsTranscoding("WEBM"))
        assertFalse(MediaClassification.needsTranscoding("MP3"))
        assertFalse(MediaClassification.needsTranscoding("Wav"))
        assertTrue(MediaClassification.needsTranscoding("MP4"))
    }

    @Test
    fun nullExtensionNeedsTranscoding() {
        assertTrue(MediaClassification.needsTranscoding(null))
    }

    @Test
    fun emptyExtensionNeedsTranscoding() {
        assertTrue(MediaClassification.needsTranscoding(""))
    }

    @Test
    fun videoExtensionsClassifyAsVideo() {
        assertTrue(MediaClassification.isVideo("mp4"))
        assertTrue(MediaClassification.isVideo("MKV"))
        assertTrue(MediaClassification.isVideo("webm"))
    }

    @Test
    fun audioExtensionsDoNotClassifyAsVideo() {
        assertFalse(MediaClassification.isVideo("mp3"))
        assertFalse(MediaClassification.isVideo("flac"))
        assertFalse(MediaClassification.isVideo("opus"))
    }

    @Test
    fun transportStreamContainersClassifyAsVideo() {
        assertTrue(MediaClassification.isVideo("mts"))
        assertTrue(MediaClassification.isVideo("m2ts"))
        assertTrue(MediaClassification.isVideo("m2t"))
    }

    @Test
    fun rawAudioExtensionsNeedTranscoding() {
        MediaClassification.rawAudioExtensions.forEach {
            assertTrue("raw codec $it must transcode", MediaClassification.needsTranscoding(it))
        }
    }
}

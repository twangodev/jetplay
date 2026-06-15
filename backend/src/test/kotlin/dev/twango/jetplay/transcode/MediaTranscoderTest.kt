package dev.twango.jetplay.transcode

import dev.twango.jetplay.media.MediaClassification
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTranscoderTest {

    @Test
    fun demuxerHintsCoverEveryRawAudioExtension() {
        // The backend supplies a demuxer hint for each headerless codec the classifier flags; a mismatch
        // means a raw-audio file routes to ffmpeg with no format set and fails to decode.
        assertEquals(MediaClassification.rawAudioExtensions, MediaTranscoder.rawAudioExtensions)
    }
}

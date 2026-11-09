package dev.twango.jetplay.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackMemoryPayloadsTest {
    @Test
    fun positionOnlyPayloadLeavesPreferencesUnset() {
        assertEquals(PlaybackMemoryUpdate(positionSeconds = 12.45), PlaybackMemoryPayloads.parseUpdate("{\"positionSeconds\":12.45}"))
    }

    @Test
    fun preferenceOnlyPayloadLeavesPositionUnset() {
        assertEquals(
            PlaybackMemoryUpdate(preferences = PlaybackPreferencePatch(0.25, true, 1.5)),
            PlaybackMemoryPayloads.parseUpdate("{\"preferences\":{\"volume\":0.25,\"muted\":true,\"playbackRate\":1.5}}"),
        )
    }

    @Test
    fun acceptsSinglePreferenceWithoutDefaultingOtherFields() {
        assertEquals(
            PlaybackMemoryUpdate(preferences = PlaybackPreferencePatch(muted = false)),
            PlaybackMemoryPayloads.parseUpdate("{\"preferences\":{\"muted\":false}}"),
        )
    }

    @Test
    fun rejectsMalformedAndInvalidPayloads() {
        val invalid = listOf(
            "not json", "[]", "{\"positionSeconds\":\"12\"}", "{\"positionSeconds\":-1}", "{\"positionSeconds\":NaN}",
            "{\"preferences\":{\"volume\":2,\"muted\":false,\"playbackRate\":1}}",
            "{\"preferences\":{\"volume\":1,\"muted\":false,\"playbackRate\":3}}",
            "{\"preferences\":{\"volume\":\"0.5\",\"muted\":false,\"playbackRate\":1}}",
            "{\"preferences\":{\"volume\":0.5,\"muted\":\"false\",\"playbackRate\":1}}",
        )
        for (json in invalid) {
            assertThrows(json, IllegalArgumentException::class.java) { PlaybackMemoryPayloads.parseUpdate(json) }
        }
    }
}

package dev.twango.jetplay.editor

import org.junit.Assert.assertNotNull
import org.junit.Test

class MediaFileIconResourcesTest {

    @Test
    fun audioAndVideoIconsArePresentInBothThemes() {
        listOf(
            "/icons/audio.svg",
            "/icons/audio_dark.svg",
            "/icons/video.svg",
            "/icons/video_dark.svg",
        ).forEach { path ->
            assertNotNull("missing icon resource $path", javaClass.getResource(path))
        }
    }
}

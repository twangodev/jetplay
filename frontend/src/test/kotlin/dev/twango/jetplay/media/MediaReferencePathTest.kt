package dev.twango.jetplay.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaReferencePathTest {
    @Test
    fun usesProjectRelativePath() {
        assertEquals("assets/voice note.mp3", mediaReferencePath("/project/assets/voice note.mp3", "/project"))
    }

    @Test
    fun acceptsTrailingProjectSeparator() {
        assertEquals("assets/demo.mp4", mediaReferencePath("/project/assets/demo.mp4", "/project/"))
    }

    @Test
    fun usesRemoteProjectPathWithoutLocalFilesystemAccess() {
        assertEquals("recordings/call.wav", mediaReferencePath("/remote/workspace/recordings/call.wav", "/remote/workspace"))
    }

    @Test
    fun normalizesWindowsSeparatorsForProjectRelativePath() {
        assertEquals("assets/demo.webm", mediaReferencePath("C:\\project\\assets\\demo.webm", "C:\\project"))
    }

    @Test
    fun doesNotStripMatchingSiblingProjectPrefix() {
        assertEquals("/project-other/demo.mp4", mediaReferencePath("/project-other/demo.mp4", "/project"))
    }

    @Test
    fun keepsOriginalPathForExternalFile() {
        assertEquals("/recordings/demo.wav", mediaReferencePath("/recordings/demo.wav", "/project"))
    }

    @Test
    fun keepsOriginalPathWhenProjectBaseIsUnavailable() {
        assertEquals("/recordings/demo.wav", mediaReferencePath("/recordings/demo.wav", null))
        assertEquals("/recordings/demo.wav", mediaReferencePath("/recordings/demo.wav", ""))
    }

    @Test
    fun supportsFilesystemRootAsProjectBase() {
        assertEquals("assets/demo.webm", mediaReferencePath("/assets/demo.webm", "/"))
    }
}

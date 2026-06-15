package dev.twango.jetplay.editor

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MediaFileEditorProviderTest : BasePlatformTestCase() {

    private lateinit var provider: MediaFileEditorProvider

    // The fileType→extension mapping ships in the frontend module descriptor, which BasePlatformTestCase
    // does not load. Register it here so the provider can resolve the Media type for these extensions.
    private val mediaExtensions = listOf(
        "mp4", "webm", "mkv", "avi", "mov", "mp3", "ogg", "wav", "flac", "aac", "opus",
    )

    override fun setUp() {
        super.setUp()
        provider = MediaFileEditorProvider()
        WriteAction.runAndWait<RuntimeException> {
            mediaExtensions.forEach { FileTypeManager.getInstance().associateExtension(MediaFileType.INSTANCE, it) }
        }
    }

    override fun tearDown() {
        try {
            WriteAction.runAndWait<RuntimeException> {
                mediaExtensions.forEach { FileTypeManager.getInstance().removeAssociatedExtension(MediaFileType.INSTANCE, it) }
            }
        } finally {
            super.tearDown()
        }
    }

    fun testAcceptsMp4() {
        val file = myFixture.addFileToProject("test.mp4", "").virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testAcceptsWebm() {
        val file = myFixture.addFileToProject("test.webm", "").virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testAcceptsMp3() {
        val file = myFixture.addFileToProject("test.mp3", "").virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testAcceptsOgg() {
        val file = myFixture.addFileToProject("test.ogg", "").virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testAcceptsWav() {
        val file = myFixture.addFileToProject("test.wav", "").virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testRejectsTxt() {
        val file = myFixture.addFileToProject("test.txt", "").virtualFile
        assertFalse(provider.accept(project, file))
    }

    fun testRejectsKt() {
        val file = myFixture.addFileToProject("test.kt", "").virtualFile
        assertFalse(provider.accept(project, file))
    }

    fun testAcceptsMkv() {
        val file = myFixture.addFileToProject("test.mkv", "").virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testAcceptsAvi() {
        val file = myFixture.addFileToProject("test.avi", "").virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testAcceptsMov() {
        val file = myFixture.addFileToProject("test.mov", "").virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testAcceptsFlac() {
        val file = myFixture.addFileToProject("test.flac", "").virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testAcceptsAac() {
        val file = myFixture.addFileToProject("test.aac", "").virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testAcceptsOpus() {
        val file = myFixture.addFileToProject("test.opus", "").virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testRejectsPng() {
        val file = myFixture.addFileToProject("test.png", "").virtualFile
        assertFalse(provider.accept(project, file))
    }

    fun testRejectsJson() {
        val file = myFixture.addFileToProject("test.json", "").virtualFile
        assertFalse(provider.accept(project, file))
    }

    fun testEditorTypeId() {
        assertEquals("media-player", provider.editorTypeId)
    }

    fun testPolicy() {
        assertEquals(
            com.intellij.openapi.fileEditor.FileEditorPolicy.HIDE_DEFAULT_EDITOR,
            provider.policy,
        )
    }
}

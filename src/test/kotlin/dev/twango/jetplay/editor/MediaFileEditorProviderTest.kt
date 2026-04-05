package dev.twango.jetplay.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MediaFileEditorProviderTest : BasePlatformTestCase() {

    private lateinit var provider: MediaFileEditorProvider

    override fun setUp() {
        super.setUp()
        provider = MediaFileEditorProvider()
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

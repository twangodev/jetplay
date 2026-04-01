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

    fun testEditorTypeId() {
        assertEquals("media-player", provider.editorTypeId)
    }

    fun testPolicy() {
        assertEquals(
            com.intellij.openapi.fileEditor.FileEditorPolicy.HIDE_DEFAULT_EDITOR,
            provider.policy
        )
    }
}
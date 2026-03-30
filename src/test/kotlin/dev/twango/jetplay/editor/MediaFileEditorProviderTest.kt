package dev.twango.jetplay.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.jcef.JBCefApp

class MediaFileEditorProviderTest : BasePlatformTestCase() {

    private lateinit var provider: MediaFileEditorProvider

    override fun setUp() {
        super.setUp()
        provider = MediaFileEditorProvider()
    }

    fun testAcceptsMp4() {
        if (!JBCefApp.isSupported()) return
        val file = myFixture.configureByText("test.mp4", "")
        assertTrue(provider.accept(project, file.virtualFile))
    }

    fun testAcceptsWebm() {
        if (!JBCefApp.isSupported()) return
        val file = myFixture.configureByText("test.webm", "")
        assertTrue(provider.accept(project, file.virtualFile))
    }

    fun testAcceptsMp3() {
        if (!JBCefApp.isSupported()) return
        val file = myFixture.configureByText("test.mp3", "")
        assertTrue(provider.accept(project, file.virtualFile))
    }

    fun testAcceptsOgg() {
        if (!JBCefApp.isSupported()) return
        val file = myFixture.configureByText("test.ogg", "")
        assertTrue(provider.accept(project, file.virtualFile))
    }

    fun testAcceptsWav() {
        if (!JBCefApp.isSupported()) return
        val file = myFixture.configureByText("test.wav", "")
        assertTrue(provider.accept(project, file.virtualFile))
    }

    fun testRejectsTxt() {
        if (!JBCefApp.isSupported()) return
        val file = myFixture.configureByText("test.txt", "")
        assertFalse(provider.accept(project, file.virtualFile))
    }

    fun testRejectsKt() {
        if (!JBCefApp.isSupported()) return
        val file = myFixture.configureByText("test.kt", "")
        assertFalse(provider.accept(project, file.virtualFile))
    }

    fun testRejectsWhenJcefUnsupported() {
        if (JBCefApp.isSupported()) return
        val file = myFixture.configureByText("test.mp4", "")
        assertFalse(provider.accept(project, file.virtualFile))
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
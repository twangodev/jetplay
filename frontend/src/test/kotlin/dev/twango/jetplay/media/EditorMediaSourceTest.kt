package dev.twango.jetplay.media

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

class EditorMediaSourceTest : BasePlatformTestCase() {

    // A real LocalFileSystem file stands in for the monolith / local-IDE case where bytes are readable in-process.
    private fun localSource(name: String): EditorMediaSource {
        val path = Files.createTempFile("jetplay-source-", "-$name")
        path.toFile().writeBytes("data".toByteArray())
        path.toFile().deleteOnExit()
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
            ?: error("could not resolve $path into the VFS")
        return EditorMediaSource(vf)
    }

    fun testLocalFileExposesNioFastPath() {
        val source = localSource("clip.mp4")
        val local = source.localFileOrNull()
        assertNotNull("a LocalFileSystem file must expose its nio path for direct serving", local)
        assertTrue(local!!.isFile)
        assertEquals("data", local.readText())
        assertFalse("a local file is never treated as remote", source.isRemote)
    }

    fun testVideoExtensionClassifiesAsVideoAndNeedsTranscoding() {
        val source = localSource("clip.mp4")
        assertTrue(source.isVideo)
        assertTrue(source.needsTranscoding)
        assertEquals("mp4", source.extension)
    }

    fun testNativeAudioNeedsNoTranscoding() {
        val source = localSource("song.mp3")
        assertFalse(source.isVideo)
        assertFalse(source.needsTranscoding)
        assertEquals("mp3", source.extension)
    }

    // A LightVirtualFile isn't backed by LocalFileSystem, standing in for a Remote Dev / Gateway file.
    fun testRemoteFileStaysEligibleForMediaInfo() {
        val remote = EditorMediaSource(LightVirtualFile("song.mp3"))
        assertTrue("a non-LocalFileSystem file must read as remote", remote.isRemote)
        assertTrue("media-info probing runs host-side, so a remote file must stay eligible", remote.canProbeMediaInfo)
    }

    fun testRawAudioIsNotEligibleForMediaInfo() {
        val raw = EditorMediaSource(LightVirtualFile("call.pcmu"))
        assertFalse("headerless raw codecs can't be probed", raw.canProbeMediaInfo)
    }
}

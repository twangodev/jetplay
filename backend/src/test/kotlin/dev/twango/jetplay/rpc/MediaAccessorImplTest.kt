package dev.twango.jetplay.rpc

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import java.nio.file.Files

class MediaAccessorImplTest : BasePlatformTestCase() {

    private val impl = MediaAccessorImpl()

    private fun fileId(bytes: ByteArray): VirtualFileId {
        val path = Files.createTempFile("jetplay-accessor-", ".bin")
        path.toFile().writeBytes(bytes)
        path.toFile().deleteOnExit()
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!.rpcId()
    }

    private fun projectId(): ProjectId = project.projectId()

    fun testStreamFileBytesReassemblesEveryByteInOrder() {
        val data = ByteArray(2048) { (it % 251).toByte() }
        val streamed = runBlocking {
            impl.streamFileBytes(fileId(data), projectId()).toList()
        }
        assertTrue("a 2KB file fits in a single 1MB chunk", streamed.size == 1)
        assertArrayEquals(data, streamed.single())
    }

    fun testStreamFileBytesChunksOnTheMegabyteBoundary() {
        // 1 MB + 17 bytes: one full chunk plus a short tail; the tail must be exactly 17 bytes, not a padded 1 MB.
        val size = (1 shl 20) + 17
        val data = ByteArray(size) { (it % 251).toByte() }
        val streamed = runBlocking {
            impl.streamFileBytes(fileId(data), projectId()).toList()
        }
        assertEquals(2, streamed.size)
        assertEquals(1 shl 20, streamed[0].size)
        assertEquals(17, streamed[1].size)
        assertArrayEquals(data, streamed[0] + streamed[1])
    }

    fun testFileLengthReportsRealSize() {
        val data = ByteArray(777)
        assertEquals(777L, runBlocking { impl.fileLength(fileId(data), projectId()) })
    }

    fun testReadRangeReturnsTheRequestedWindow() {
        val data = ByteArray(100) { it.toByte() }
        val window = runBlocking { impl.readRange(fileId(data), projectId(), offset = 10, length = 5) }
        assertArrayEquals(byteArrayOf(10, 11, 12, 13, 14), window)
    }

    fun testReadRangePastEndIsTruncatedToWhatExists() {
        val data = ByteArray(8) { it.toByte() }
        val window = runBlocking { impl.readRange(fileId(data), projectId(), offset = 5, length = 100) }
        assertArrayEquals(byteArrayOf(5, 6, 7), window)
    }
}

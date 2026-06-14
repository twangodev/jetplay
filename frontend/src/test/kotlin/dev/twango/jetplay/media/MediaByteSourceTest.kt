package dev.twango.jetplay.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaByteSourceTest {

    @Test
    fun remoteSourceDelegatesReadToReader() {
        val data = ByteArray(100) { it.toByte() }
        val source = RemoteRangeByteSource(
            length = data.size.toLong(),
            contentType = "video/mp4",
        ) { offset, len -> data.copyOfRange(offset.toInt(), offset.toInt() + len) }

        assertEquals(100L, source.length)
        assertEquals("video/mp4", source.contentType)
        assertArrayEquals(byteArrayOf(10, 11, 12), source.read(10, 3))
    }

    @Test
    fun remoteSourceRejectsNonPositiveLength() {
        val source = RemoteRangeByteSource(10, "video/mp4") { _, _ -> ByteArray(0) }
        assertArrayEquals(ByteArray(0), source.read(0, 0))
        assertArrayEquals(ByteArray(0), source.read(-1, 5))
    }
}

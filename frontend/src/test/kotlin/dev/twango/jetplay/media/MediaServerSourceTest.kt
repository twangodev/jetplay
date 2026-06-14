package dev.twango.jetplay.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI

class MediaServerSourceTest {

    @Test
    fun servesRangeFromRemoteSource() {
        val data = ByteArray(1000) { (it % 256).toByte() }
        val source = RemoteRangeByteSource(data.size.toLong(), "video/mp4") { off, len ->
            data.copyOfRange(off.toInt(), minOf(off.toInt() + len, data.size))
        }
        val url = MediaServer.serve(source)
        try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.setRequestProperty("Range", "bytes=10-19")
            conn.setRequestProperty("Host", "127.0.0.1")
            assertEquals(206, conn.responseCode)
            assertEquals("bytes 10-19/1000", conn.getHeaderField("Content-Range"))
            assertArrayEquals(data.copyOfRange(10, 20), conn.inputStream.readBytes())
            conn.disconnect()
        } finally {
            MediaServer.release(url)
        }
    }

    @Test
    fun servesFullBodyFromRemoteSource() {
        val data = ByteArray(500) { (it % 256).toByte() }
        val source = RemoteRangeByteSource(data.size.toLong(), "audio/mpeg") { off, len ->
            data.copyOfRange(off.toInt(), minOf(off.toInt() + len, data.size))
        }
        val url = MediaServer.serve(source)
        try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.setRequestProperty("Host", "127.0.0.1")
            assertEquals(200, conn.responseCode)
            assertArrayEquals(data, conn.inputStream.readBytes())
            conn.disconnect()
        } finally {
            MediaServer.release(url)
        }
    }
}

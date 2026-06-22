package dev.twango.jetplay.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
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
    fun abortsConnectionWhenSourceReadFails() {
        val source = RemoteRangeByteSource(1000, "video/mp4") { _, _ ->
            throw IllegalStateException("simulated RPC read timeout")
        }
        val url = MediaServer.serve(source)
        try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.setRequestProperty("Host", "127.0.0.1")
            // Server aborts the connection on read failure rather than completing a truncated body, so reading it fails.
            try {
                conn.responseCode
                conn.inputStream.readBytes()
                org.junit.Assert.fail("expected aborted connection")
            } catch (_: IOException) {
            }
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

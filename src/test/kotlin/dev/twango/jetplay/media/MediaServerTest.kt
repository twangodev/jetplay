package dev.twango.jetplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI
import java.nio.file.Files

class MediaServerTest {

    @Test
    fun servesRegisteredFileWithCors() {
        withTempFile("hello world".toByteArray(), ".mp3") { file ->
            val conn = open(MediaServer.serve(file))
            assertEquals(200, conn.responseCode)
            assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"))
            assertEquals("bytes", conn.getHeaderField("Accept-Ranges"))
            assertEquals("hello world", conn.inputStream.readBytes().decodeToString())
            conn.disconnect()
        }
    }

    @Test
    fun servesRangeRequestsAsPartialContent() {
        withTempFile("0123456789".toByteArray(), ".bin") { file ->
            val conn = open(MediaServer.serve(file))
            conn.setRequestProperty("Range", "bytes=2-5")
            assertEquals(206, conn.responseCode)
            assertEquals("bytes 2-5/10", conn.getHeaderField("Content-Range"))
            assertEquals("2345", conn.inputStream.readBytes().decodeToString())
            conn.disconnect()
        }
    }

    @Test
    fun servesSuffixRangeAsTailBytes() {
        withTempFile("0123456789".toByteArray(), ".bin") { file ->
            val conn = open(MediaServer.serve(file))
            conn.setRequestProperty("Range", "bytes=-3")
            assertEquals(206, conn.responseCode)
            assertEquals("bytes 7-9/10", conn.getHeaderField("Content-Range"))
            assertEquals("789", conn.inputStream.readBytes().decodeToString())
            conn.disconnect()
        }
    }

    @Test
    fun rangePastEndReturns416() {
        withTempFile("0123456789".toByteArray(), ".bin") { file ->
            val conn = open(MediaServer.serve(file))
            conn.setRequestProperty("Range", "bytes=100-200")
            assertEquals(416, conn.responseCode)
            conn.disconnect()
        }
    }

    @Test
    fun rejectsNonLoopbackHost() {
        withTempFile("x".toByteArray(), ".mp3") { file ->
            val uri = URI(MediaServer.serve(file))
            Socket(uri.host, uri.port).use { socket ->
                socket.getOutputStream().apply {
                    write("GET ${uri.path} HTTP/1.1\r\nHost: evil.attacker.com\r\nConnection: close\r\n\r\n".toByteArray())
                    flush()
                }
                val statusLine = socket.getInputStream().bufferedReader().readLine() ?: ""
                assertTrue("expected 403, got: $statusLine", statusLine.contains("403"))
            }
        }
    }

    @Test
    fun unknownTokenReturns404() {
        withTempFile("x".toByteArray(), ".mp3") { file ->
            val base = MediaServer.serve(file).substringBeforeLast('/')
            val conn = open("$base/deadbeefdeadbeef")
            assertEquals(404, conn.responseCode)
            conn.disconnect()
        }
    }

    @Test
    fun releasedTokenStopsBeingServed() {
        withTempFile("bye".toByteArray(), ".mp3") { file ->
            val url = MediaServer.serve(file)
            assertEquals(200, open(url).also { it.disconnect() }.responseCode)
            MediaServer.release(url)
            assertEquals(404, open(url).also { it.disconnect() }.responseCode)
        }
    }

    @Test
    fun servesOnLoopbackOnly() {
        withTempFile("x".toByteArray(), ".mp3") { file ->
            assertTrue(MediaServer.serve(file).startsWith("http://127.0.0.1:"))
        }
    }

    private fun open(url: String): HttpURLConnection = (URI(url).toURL().openConnection() as HttpURLConnection)

    private fun withTempFile(bytes: ByteArray, suffix: String, block: (File) -> Unit) {
        val file = Files.createTempFile("jetplay-server-test", suffix).toFile().apply { writeBytes(bytes) }
        try {
            block(file)
        } finally {
            file.delete()
        }
    }
}

package dev.twango.jetplay.media

import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Tiny loopback HTTP server that streams registered local media files to the
 * JCEF browser. Serving over http with CORS + range — rather than file:// —
 * lets the page fetch()/decode the audio (the scrubber's scratch buffer,
 * in-browser waveform decode) and range-seek large files, none of which a
 * null-origin file:// page can do.
 *
 * Security: binds 127.0.0.1 only; serves ONLY files registered via [serve],
 * each under an unguessable random token (no directory listing, no traversal).
 */
object MediaServer {

    private val log = Logger.getInstance(MediaServer::class.java)
    private val files = ConcurrentHashMap<String, File>()
    private const val CHUNK = 64 * 1024

    private const val HTTP_OK = 200
    private const val HTTP_NO_CONTENT = 204
    private const val HTTP_PARTIAL_CONTENT = 206
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_RANGE_NOT_SATISFIABLE = 416

    @Volatile
    private var server: HttpServer? = null

    /** Registers [file] and returns a loopback URL the browser can fetch + play. */
    @Synchronized
    fun serve(file: File): String {
        val srv = server ?: start().also { server = it }
        val token = UUID.randomUUID().toString().replace("-", "")
        files[token] = file
        return "http://127.0.0.1:${srv.address.port}/$token"
    }

    /** Stops serving the file behind [url]. */
    fun release(url: String) {
        files.remove(url.substringAfterLast('/'))
    }

    private fun start(): HttpServer {
        val srv = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        srv.executor = Executors.newCachedThreadPool { r ->
            Thread(r, "jetplay-media-server").apply { isDaemon = true }
        }
        srv.createContext("/", ::handle)
        srv.start()
        log.info("Media server listening on 127.0.0.1:${srv.address.port}")
        return srv
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val headers = exchange.responseHeaders
            // The JCEF player page is a null-origin loadHTML document, so it has
            // no stable origin to allowlist — ACAO:* is effectively required for
            // it to fetch()/decode the media. The real boundary is the 122-bit
            // token plus the loopback bind, Host check, and per-editor release.
            headers.add("Access-Control-Allow-Origin", "*")

            if (exchange.requestMethod == "OPTIONS") {
                headers.add("Access-Control-Allow-Methods", "GET, OPTIONS")
                headers.add("Access-Control-Allow-Headers", "Range")
                // Only the preflight needs Chrome's Private Network Access opt-in.
                headers.add("Access-Control-Allow-Private-Network", "true")
                exchange.sendResponseHeaders(HTTP_NO_CONTENT, -1)
                return
            }

            // Reject non-loopback Host headers to neutralize DNS rebinding.
            if (!isLoopbackHost(exchange.requestHeaders.getFirst("Host"))) {
                exchange.sendResponseHeaders(HTTP_FORBIDDEN, -1)
                return
            }

            val file = files[exchange.requestURI.path.trimStart('/')]
            if (file == null || !file.isFile) {
                exchange.sendResponseHeaders(HTTP_NOT_FOUND, -1)
                return
            }

            headers.add("Content-Type", contentType(file))
            headers.add("Accept-Ranges", "bytes")
            val length = file.length()
            // Only a single-range "bytes=" request gets partial content; multi-range,
            // garbage, and empty files fall back to a full 200.
            val rangeHeader = exchange.requestHeaders.getFirst("Range")
                ?.takeIf { it.startsWith("bytes=") && !it.contains(',') }
            if (rangeHeader != null && length > 0) {
                writeRange(exchange, file, length, rangeHeader)
            } else {
                exchange.sendResponseHeaders(HTTP_OK, if (length == 0L) -1 else length)
                file.inputStream().use { it.copyTo(exchange.responseBody) }
            }
        } catch (e: Exception) {
            log.warn("Media server request failed", e)
        } finally {
            exchange.close()
        }
    }

    private fun isLoopbackHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val name = if (host.startsWith("[")) {
            host.substringAfter("[").substringBefore("]") // [::1]:port
        } else {
            host.substringBefore(":") // 127.0.0.1:port / localhost:port
        }
        return name.equals("127.0.0.1", true) || name.equals("localhost", true) || name.equals("::1", true)
    }

    private fun writeRange(exchange: HttpExchange, file: File, length: Long, range: String) {
        val spec = range.removePrefix("bytes=").split('-', limit = 2)
        val startTok = spec.getOrNull(0)?.trim().orEmpty()
        val endTok = spec.getOrNull(1)?.trim().orEmpty()

        val start: Long
        val end: Long
        if (startTok.isEmpty()) {
            // Suffix range "bytes=-N": the LAST n bytes.
            val n = endTok.toLongOrNull()
            if (n == null || n <= 0) return send416(exchange, length)
            start = maxOf(0, length - n)
            end = length - 1
        } else {
            val s = startTok.toLongOrNull()
            if (s == null || s >= length) return send416(exchange, length)
            start = s
            end = (endTok.toLongOrNull() ?: (length - 1)).coerceIn(start, length - 1)
        }

        val count = end - start + 1
        exchange.responseHeaders.add("Content-Range", "bytes $start-$end/$length")
        exchange.sendResponseHeaders(HTTP_PARTIAL_CONTENT, count)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buffer = ByteArray(CHUNK)
            var remaining = count
            while (remaining > 0) {
                val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read == -1) break
                exchange.responseBody.write(buffer, 0, read)
                remaining -= read
            }
        }
    }

    private fun send416(exchange: HttpExchange, length: Long) {
        exchange.responseHeaders.add("Content-Range", "bytes */$length")
        exchange.sendResponseHeaders(HTTP_RANGE_NOT_SATISFIABLE, -1)
    }

    private fun contentType(file: File): String = runCatching { Files.probeContentType(file.toPath()) }.getOrNull()
        ?: when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "webm" -> "video/webm"
            "mp4", "m4v" -> "video/mp4"
            "ogv" -> "video/ogg"
            else -> "application/octet-stream"
        }
}

package dev.twango.jetplay.media

import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Loopback HTTP server streaming registered local media to JCEF. http+CORS+range
 * (not file://) lets the null-origin page fetch()/decode audio and range-seek large files.
 *
 * Security: binds 127.0.0.1 only; serves ONLY files registered via [serve], each
 * behind an unguessable random token (no directory listing, no traversal).
 */
object MediaServer {

    private val log = Logger.getInstance(MediaServer::class.java)
    private val files = ConcurrentHashMap<String, File>()

    // Tokens the browser has actually fetched. Drives the load watchdog: a served-but-never-fetched
    // token means the loopback URL is unreachable from the (possibly remote-dev/frontend-side) Chromium.
    private val fetched = ConcurrentHashMap.newKeySet<String>()
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

    /** True once the browser has fetched [url] at least once (any method, any range). */
    fun wasFetched(url: String): Boolean = fetched.contains(tokenOf(url))

    /** Stops serving the file behind [url]. */
    fun release(url: String) {
        val token = tokenOf(url)
        files.remove(token)
        fetched.remove(token)
    }

    // Mirror [handle]'s extraction (request path, query/fragment stripped) so fetch-state lookups never drift from the served key.
    private fun tokenOf(url: String): String =
        runCatching { URI(url).path }.getOrNull()?.trimStart('/') ?: url.substringAfterLast('/')

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
        // Per-request trace: the only window into split-mode serving when playback silently stalls on a remote host.
        if (log.isDebugEnabled) {
            log.debug("${exchange.requestMethod} ${exchange.requestURI.path} range=${exchange.requestHeaders.getFirst("Range")}")
        }
        try {
            val headers = exchange.responseHeaders
            // Null-origin JCEF page has no origin to allowlist; security rests on the random token + loopback bind + Host check.
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

            val token = exchange.requestURI.path.trimStart('/')
            val file = files[token]
            if (file == null || !file.isFile) {
                // A live editor requesting an unknown/vanished token signals a load-path failure, not a benign 404.
                log.warn("Media request for missing file: ${exchange.requestURI.path} (registered=${file != null})")
                exchange.sendResponseHeaders(HTTP_NOT_FOUND, -1)
                return
            }
            // First reachable fetch of this token: the watchdog reads this to tell a stalled load
            // (URL never reached the browser) from a genuinely playing one.
            if (fetched.add(token)) log.debug("First media fetch for token $token")

            headers.add("Content-Type", contentType(file))
            headers.add("Accept-Ranges", "bytes")
            val length = file.length()
            val singleByteRange = exchange.requestHeaders.getFirst("Range")
                ?.takeIf { it.startsWith("bytes=") && !it.contains(',') }
            if (singleByteRange != null && length > 0) {
                writeRange(exchange, file, length, singleByteRange)
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

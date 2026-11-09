package dev.twango.jetplay.media

import java.io.File

/** Owns each loopback URL and its optional temporary file through one atomic registration. */
internal class MediaResources(
    private val serve: (MediaByteSource) -> String = MediaServer::serve,
    private val release: (String) -> Unit = MediaServer::release,
) {
    private val lock = Any()
    private val registrations = mutableListOf<Registration>()
    private var disposed = false

    fun register(file: File, temporary: Boolean = false): String? =
        register(FileByteSource(file), file.takeIf { temporary })

    fun register(source: MediaByteSource): String? = register(source, null)

    private fun register(source: MediaByteSource, temporaryFile: File?): String? = synchronized(lock) {
        if (disposed) {
            temporaryFile?.delete()
            return@synchronized null
        }
        val url = serve(source)
        registrations.add(Registration(url, temporaryFile))
        url
    }

    fun dispose() {
        val owned = synchronized(lock) {
            if (disposed) return
            disposed = true
            registrations.toList().also { registrations.clear() }
        }
        owned.forEach {
            try {
                release(it.url)
            } finally {
                it.temporaryFile?.delete()
            }
        }
    }

    private data class Registration(val url: String, val temporaryFile: File?)
}

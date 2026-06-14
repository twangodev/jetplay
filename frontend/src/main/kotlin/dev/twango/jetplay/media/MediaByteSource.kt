package dev.twango.jetplay.media

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

interface MediaByteSource {
    val length: Long?
    val contentType: String?

    fun read(offset: Long, length: Int): ByteArray
}

class FileByteSource(private val file: File) : MediaByteSource {
    override val length: Long? get() = if (file.isFile) file.length() else null
    override val contentType: String? get() = contentTypeForFile(file)

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || length <= 0 || !file.isFile) return ByteArray(0)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val out = ByteArray(length)
            var total = 0
            while (total < length) {
                val n = raf.read(out, total, length - total)
                if (n < 0) break
                total += n
            }
            return when (total) {
                0 -> ByteArray(0)
                length -> out
                else -> out.copyOf(total)
            }
        }
    }
}

class RemoteRangeByteSource(
    override val length: Long?,
    override val contentType: String?,
    private val reader: (offset: Long, length: Int) -> ByteArray,
) : MediaByteSource {
    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || length <= 0) return ByteArray(0)
        return reader(offset, length)
    }
}

internal fun contentTypeForFile(file: File): String =
    runCatching { Files.probeContentType(file.toPath()) }.getOrNull() ?: contentTypeForExtension(file.extension)

internal fun contentTypeForExtension(extension: String): String = when (extension.lowercase()) {
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

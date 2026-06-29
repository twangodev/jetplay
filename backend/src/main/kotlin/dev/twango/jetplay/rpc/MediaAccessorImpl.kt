@file:Suppress("UnstableApiUsage")

package dev.twango.jetplay.rpc

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import dev.twango.jetplay.media.MediaInfo
import dev.twango.jetplay.media.Spectrogram
import dev.twango.jetplay.transcode.FfmpegAvailability
import dev.twango.jetplay.transcode.MediaInfoExtractor
import dev.twango.jetplay.transcode.SpectrogramExtractor
import dev.twango.jetplay.transcode.TranscodeRunner
import dev.twango.jetplay.transcode.WaveformExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

private const val CHUNK_BYTES = 1 shl 20 // 1 MB

class MediaAccessorImpl : MediaAccessor {

    // Resolve only within a live project; a dead projectId means a stale RPC caller.
    private fun resolveFile(fileId: VirtualFileId, projectId: ProjectId): File? {
        if (projectId.findProjectOrNull() == null) return null
        return fileId.virtualFile()?.takeIf { it.isValid }?.let { vf ->
            runCatching { vf.toNioPath().toFile() }.getOrNull()?.takeIf { it.isFile }
        }
    }

    override suspend fun streamFileBytes(fileId: VirtualFileId, projectId: ProjectId): Flow<ByteArray> = flow {
        val file = resolveFile(fileId, projectId) ?: return@flow
        RandomAccessFile(file, "r").use { raf ->
            val buf = ByteArray(CHUNK_BYTES)
            while (true) {
                val n = raf.read(buf)
                if (n <= 0) break
                emit(if (n == buf.size) buf.copyOf() else buf.copyOf(n))
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun fileLength(fileId: VirtualFileId, projectId: ProjectId): Long =
        withContext(Dispatchers.IO) { resolveFile(fileId, projectId)?.length() ?: -1L }

    override suspend fun readRange(fileId: VirtualFileId, projectId: ProjectId, offset: Long, length: Int): ByteArray =
        withContext(Dispatchers.IO) {
            if (offset < 0 || length <= 0) return@withContext ByteArray(0)
            val file = resolveFile(fileId, projectId) ?: return@withContext ByteArray(0)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                val out = ByteArray(length)
                // raf.read() may return a short count; loop until full or EOF.
                var total = 0
                while (total < length) {
                    val n = raf.read(out, total, length - total)
                    if (n < 0) break
                    total += n
                }
                when (total) {
                    0 -> ByteArray(0)
                    length -> out
                    else -> out.copyOf(total)
                }
            }
        }

    override suspend fun transcodeFile(fileId: VirtualFileId, projectId: ProjectId): Flow<TranscodeEvent> =
        channelFlow {
            if (!FfmpegAvailability.available) {
                send(TranscodeEvent.Unavailable)
                return@channelFlow
            }
            val input = resolveFile(fileId, projectId) ?: run {
                send(TranscodeEvent.Failed("source unavailable"))
                return@channelFlow
            }
            val output = try {
                withContext(Dispatchers.IO) {
                    // onProgress fires synchronously inside ffmpeg, so trySend (non-suspending) bridges it.
                    TranscodeRunner.transcode(input) { pct -> trySend(TranscodeEvent.Progress(pct)) }
                }
            } catch (e: Exception) {
                send(TranscodeEvent.Failed(e.message ?: "unknown"))
                return@channelFlow
            }
            try {
                withContext(Dispatchers.IO) {
                    RandomAccessFile(output, "r").use { raf ->
                        val buf = ByteArray(CHUNK_BYTES)
                        while (true) {
                            val n = raf.read(buf)
                            if (n <= 0) break
                            send(TranscodeEvent.Chunk(if (n == buf.size) buf.copyOf() else buf.copyOf(n)))
                        }
                    }
                }
            } finally {
                // Frontend now holds the bytes; drop the backend copy.
                runCatching { output.delete() }
            }
            send(TranscodeEvent.Done)
        }

    // Shared shell for the extract* RPCs: ffmpeg gate, then file resolve, then a guarded extract.
    private suspend fun <T> withFfmpegResolvedFile(
        fileId: VirtualFileId,
        projectId: ProjectId,
        default: T,
        extract: (File) -> T,
    ): T = withContext(Dispatchers.IO) {
        if (!FfmpegAvailability.available) return@withContext default
        val file = resolveFile(fileId, projectId) ?: return@withContext default
        runCatching { extract(file) }.getOrDefault(default)
    }

    override suspend fun extractWaveform(fileId: VirtualFileId, projectId: ProjectId): List<Double> =
        withFfmpegResolvedFile(fileId, projectId, emptyList()) { WaveformExtractor.extract(it) }

    override suspend fun extractMediaInfo(fileId: VirtualFileId, projectId: ProjectId): MediaInfo? =
        withFfmpegResolvedFile(fileId, projectId, null) { MediaInfoExtractor.extract(it) }

    override suspend fun extractSpectrogram(fileId: VirtualFileId, projectId: ProjectId): Spectrogram? =
        withFfmpegResolvedFile(fileId, projectId, null) { SpectrogramExtractor.extract(it) }
}

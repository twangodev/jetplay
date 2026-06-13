@file:Suppress("UnstableApiUsage")

package dev.twango.jetplay.rpc

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import dev.twango.jetplay.media.MediaInfo
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface MediaAccessor : RemoteApi<Unit> {
    /** Stream raw source bytes in order. Primary path; element type is plain ByteArray (Serializable). */
    suspend fun streamFileBytes(fileId: VirtualFileId, projectId: ProjectId): Flow<ByteArray>

    /** Fallback random-access read; always available even if Flow streaming underperforms on large media. */
    suspend fun fileLength(fileId: VirtualFileId, projectId: ProjectId): Long

    suspend fun readRange(fileId: VirtualFileId, projectId: ProjectId, offset: Long, length: Int): ByteArray

    /** Transcode to WebM on backend; emit progress then the transcoded bytes as a stream. */
    suspend fun transcodeFile(fileId: VirtualFileId, projectId: ProjectId): Flow<TranscodeEvent>

    /** Empty list if ffmpeg unavailable or format unsupported (NEVER throws to caller). */
    suspend fun extractWaveform(fileId: VirtualFileId, projectId: ProjectId): List<Double>

    /** null if ffmpeg unavailable or no readable stream. */
    suspend fun extractMediaInfo(fileId: VirtualFileId, projectId: ProjectId): MediaInfo?

    companion object {
        suspend fun getInstance(): MediaAccessor =
            RemoteApiProviderService.resolve(remoteApiDescriptor<MediaAccessor>())
    }
}

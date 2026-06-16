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

@Rpc
interface MediaAccessor : RemoteApi<Unit> {
    /** Primary path: stream raw source bytes in order. */
    suspend fun streamFileBytes(fileId: VirtualFileId, projectId: ProjectId): Flow<ByteArray>

    /** Fallback random-access read for when Flow streaming underperforms. */
    suspend fun fileLength(fileId: VirtualFileId, projectId: ProjectId): Long

    suspend fun readRange(fileId: VirtualFileId, projectId: ProjectId, offset: Long, length: Int): ByteArray

    /** Transcode to WebM on backend, emitting progress then bytes. */
    suspend fun transcodeFile(fileId: VirtualFileId, projectId: ProjectId): Flow<TranscodeEvent>

    /** Never throws: empty list if ffmpeg unavailable or format unsupported. */
    suspend fun extractWaveform(fileId: VirtualFileId, projectId: ProjectId): List<Double>

    /** null if ffmpeg unavailable or no readable stream. */
    suspend fun extractMediaInfo(fileId: VirtualFileId, projectId: ProjectId): MediaInfo?

    companion object {
        suspend fun getInstance(): MediaAccessor =
            RemoteApiProviderService.resolve(remoteApiDescriptor<MediaAccessor>())
    }
}

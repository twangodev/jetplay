package dev.twango.jetplay.rpc

import kotlinx.serialization.Serializable

@Serializable
sealed interface TranscodeEvent {
    @Serializable
    data class Progress(val percent: Double) : TranscodeEvent

    /** Ordered output chunk of the transcoded WebM. */
    @Serializable
    data class Chunk(val bytes: ByteArray) : TranscodeEvent {
        override fun equals(other: Any?) = this === other || (other is Chunk && bytes.contentEquals(other.bytes))
        override fun hashCode() = bytes.contentHashCode()
    }

    @Serializable
    data object Done : TranscodeEvent

    /** Raw (un-localized) error string; frontend wraps with JetPlayBundle. */
    @Serializable
    data class Failed(val message: String) : TranscodeEvent

    /** ffmpeg not available on backend — frontend shows transcoding-error state. */
    @Serializable
    data object Unavailable : TranscodeEvent
}

package dev.twango.jetplay.media

import kotlinx.serialization.Serializable

/** Codec-inspector metadata; nullable fields let the UI skip anything FFmpeg couldn't determine. */
@Serializable
data class MediaInfo(
    val codec: String?,
    val container: String?,
    val sampleRateHz: Int?,
    val channels: Int?,
    val channelLabel: String?,
    /** Only set when meaningful (PCM / lossless). Null for lossy codecs. */
    val bitDepth: String?,
    val bitrateBps: Long?,
    val durationMs: Long?,
    val sizeBytes: Long?,
    // Video-stream fields (null for audio-only files).
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val videoCodec: String? = null,
    val pixelFormat: String? = null,
    val videoBitrateBps: Long? = null,
    /** Embedded text tags (title/artist/album/…), in display order. */
    val tags: List<MediaTag> = emptyList(),
    /** Embedded cover art as a `data:` URL, or null when there is none. */
    val albumArt: String? = null,
)

/** One embedded metadata tag, already labeled for display. */
@Serializable
data class MediaTag(val label: String, val value: String)

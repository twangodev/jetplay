package dev.twango.jetplay.media

import kotlinx.serialization.Serializable

/** Null fields mark anything FFmpeg couldn't determine. */
@Serializable
data class MediaInfo(
    val codec: String?,
    val container: String?,
    val sampleRateHz: Int?,
    val channels: Int?,
    val channelLabel: String?,
    /** Set only for PCM/lossless; null for lossy codecs. */
    val bitDepth: String?,
    val bitrateBps: Long?,
    val durationMs: Long?,
    val sizeBytes: Long?,
    // Null for audio-only files.
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val videoCodec: String? = null,
    val pixelFormat: String? = null,
    val videoBitrateBps: Long? = null,
    /** Embedded text tags, in display order. */
    val tags: List<MediaTag> = emptyList(),
    /** Cover art as a `data:` URL. */
    val albumArt: String? = null,
)

/** One embedded metadata tag, already labeled for display. */
@Serializable
data class MediaTag(val label: String, val value: String)

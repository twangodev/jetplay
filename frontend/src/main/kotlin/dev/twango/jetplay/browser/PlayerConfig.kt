package dev.twango.jetplay.browser

import dev.twango.jetplay.playback.PlaybackMemorySnapshot
import kotlinx.serialization.Serializable

@Serializable
data class PlayerConfig(
    val state: String = "ready",
    val isVideo: Boolean = false,
    val fileName: String = "",
    val filePath: String = "",
    val fileExtension: String = "",
    val mediaUrl: String? = null,
    val errorMessage: String? = null,
    val transcodingReason: String? = null,
    val ui: UiStrings = UiStrings(),
    val playbackMemory: PlaybackMemorySnapshot = PlaybackMemorySnapshot(),
)

@Serializable
data class UiStrings(
    val transcodingLabel: String? = null,
    val transcodingTip: String? = null,
    val errorTitle: String? = null,
)

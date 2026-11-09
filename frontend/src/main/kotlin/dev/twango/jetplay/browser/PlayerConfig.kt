package dev.twango.jetplay.browser

import dev.twango.jetplay.playback.PlaybackMemorySnapshot

data class PlayerConfig(
    val state: String = "ready",
    val isVideo: Boolean = false,
    val fileName: String = "",
    val filePath: String = "",
    val fileExtension: String = "",
    val mediaUrl: String? = null,
    val errorMessage: String = "",
    val transcodingReason: String = "",
    val ui: UiStrings = UiStrings(),
    val playbackMemory: PlaybackMemorySnapshot = PlaybackMemorySnapshot(),
)

data class UiStrings(val transcodingLabel: String = "", val transcodingTip: String = "", val errorTitle: String = "")

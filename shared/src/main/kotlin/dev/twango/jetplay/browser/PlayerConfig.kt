package dev.twango.jetplay.browser

data class PlayerConfig(
    val state: String = "ready",
    val isVideo: Boolean = false,
    val fileName: String = "",
    val fileExtension: String = "",
    val mediaUrl: String? = null,
    val errorMessage: String = "",
    val transcodingReason: String = "",
    val downloadingReason: String = "",
    val ui: UiStrings = UiStrings(),
)

data class UiStrings(
    val downloadingLabel: String = "",
    val transcodingLabel: String = "",
    val transcodingTip: String = "",
    val errorTitle: String = "",
)

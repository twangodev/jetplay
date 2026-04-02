package dev.twango.jetplay.browser

data class PlayerConfig(
    val state: String = "ready",
    val isVideo: Boolean = false,
    val fileName: String = "",
    val fileExtension: String = "",
    val mediaUrl: String? = null,
    val errorMessage: String = "",
    val transcodingReason: String = ""
)

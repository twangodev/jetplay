package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger

internal inline fun Logger.safely(action: String, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        warn("$action failed", e)
    }
}

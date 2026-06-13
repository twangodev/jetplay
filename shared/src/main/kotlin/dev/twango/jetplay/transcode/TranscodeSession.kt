package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import dev.twango.jetplay.JetPlayBundle
import dev.twango.jetplay.browser.PlayerBridge
import java.io.File
import kotlin.concurrent.thread

class TranscodeSession(
    private val inputFile: File,
    private val bridge: PlayerBridge,
    private val onReady: (File) -> Unit,
) {

    companion object {
        private val log = Logger.getInstance(TranscodeSession::class.java)
    }

    @Volatile
    var cancelled = false
        private set

    private var thread: Thread? = null

    // Serializes cancel() against the onReady handoff so a racing cancel can't slip between the check and the callback.
    private val readyLock = Any()

    private fun deliverIfActive(file: File) = synchronized(readyLock) {
        if (!cancelled) {
            onReady(file)
        }
    }

    fun start() {
        thread = thread(name = "jetplay-transcode", isDaemon = true) {
            try {
                val transcoded = MediaTranscoder.transcode(inputFile) { percent ->
                    if (!cancelled) bridge.updateProgress(percent)
                }
                deliverIfActive(transcoded)
            } catch (_: InterruptedException) {
                log.info("Transcoding interrupted for ${inputFile.name}")
            } catch (e: Exception) {
                log.warn("Transcoding failed for ${inputFile.name}", e)
                if (!cancelled) {
                    bridge.showError(e.message ?: JetPlayBundle.message("error.unknown"))
                }
            }
        }
    }

    fun cancel() {
        synchronized(readyLock) { cancelled = true }
        thread?.interrupt()
    }
}

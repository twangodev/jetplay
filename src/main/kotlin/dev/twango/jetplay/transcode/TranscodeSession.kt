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

    // Guards the cancelled-then-onReady handoff so cancel() can't slip its
    // release pass between the check and the callback (which re-serves media).
    private val readyLock = Any()

    fun start() {
        thread = thread(name = "jetplay-transcode", isDaemon = true) {
            try {
                val transcoded = MediaTranscoder.transcode(inputFile) { percent ->
                    if (!cancelled) bridge.updateProgress(percent)
                }
                // Re-check under the lock so a racing cancel() either wins (we
                // skip onReady) or completes only after onReady has re-served.
                synchronized(readyLock) {
                    if (!cancelled) onReady(transcoded)
                }
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

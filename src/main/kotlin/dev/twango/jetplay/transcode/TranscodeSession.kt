package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import dev.twango.jetplay.browser.PlayerBridge
import java.io.File
import kotlin.concurrent.thread

class TranscodeSession(
    private val inputFile: File,
    private val bridge: PlayerBridge
) {

    companion object {
        private val log = Logger.getInstance(TranscodeSession::class.java)
    }

    @Volatile
    var cancelled = false
        private set

    private var thread: Thread? = null

    fun start() {
        thread = thread(name = "jetplay-transcode", isDaemon = true) {
            try {
                val transcoded = MediaTranscoder.transcode(inputFile) { percent ->
                    if (!cancelled) bridge.updateProgress(percent)
                }
                if (!cancelled) {
                    bridge.mediaReady(transcoded.toURI().toString())
                }
            } catch (_: InterruptedException) {
                log.info("Transcoding interrupted for ${inputFile.name}")
            } catch (e: Exception) {
                log.warn("Transcoding failed for ${inputFile.name}", e)
                if (!cancelled) {
                    bridge.showError(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun cancel() {
        cancelled = true
        thread?.interrupt()
    }
}

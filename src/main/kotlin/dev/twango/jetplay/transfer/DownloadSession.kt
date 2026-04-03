package dev.twango.jetplay.transfer

import com.intellij.openapi.diagnostic.Logger
import dev.twango.jetplay.JetPlayBundle
import dev.twango.jetplay.JetPlayConstants
import dev.twango.jetplay.browser.PlayerBridge
import dev.twango.jetplay.media.RemoteFileMediaSource
import java.io.File
import kotlin.concurrent.thread

class DownloadSession(
    private val source: RemoteFileMediaSource,
    private val bridge: PlayerBridge,
    private val onComplete: (File) -> Unit,
) {

    companion object {
        private val log = Logger.getInstance(DownloadSession::class.java)
    }

    @Volatile
    var cancelled = false
        private set

    private var thread: Thread? = null

    fun start() {
        thread = thread(name = "jetplay-download", isDaemon = true) {
            try {
                val tempFile = File.createTempFile("jetplay-", ".${source.extension}").apply {
                    deleteOnExit()
                }
                val totalBytes = source.fileSize
                var bytesRead = 0L
                var lastReportedPercent = -10.0

                source.inputStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var n: Int
                        while (input.read(buffer).also { n = it } != -1) {
                            if (cancelled) return@thread
                            output.write(buffer, 0, n)
                            bytesRead += n
                            if (totalBytes > 0) {
                                val percent = (bytesRead.toDouble() / totalBytes) * 100
                                if (percent - lastReportedPercent >= 1.0) {
                                    bridge.updateDownloadProgress(percent)
                                    lastReportedPercent = percent
                                }
                            }
                        }
                    }
                }

                if (!cancelled) {
                    source.setLocalFile(tempFile)
                    log.info("Downloaded ${source.fileName} (${tempFile.length() / JetPlayConstants.BYTES_PER_KB} KB)")
                    onComplete(tempFile)
                }
            } catch (_: InterruptedException) {
                log.info("Download interrupted for ${source.fileName}")
            } catch (e: Exception) {
                log.warn("Download failed for ${source.fileName}", e)
                if (!cancelled) {
                    val errorMsg = e.message ?: JetPlayBundle.message("error.unknown")
                    bridge.showError(JetPlayBundle.message("error.download", errorMsg))
                }
            }
        }
    }

    fun cancel() {
        cancelled = true
        thread?.interrupt()
    }
}

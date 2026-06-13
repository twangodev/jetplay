package dev.twango.jetplay.editor

import com.intellij.ide.BrowserUtil
import com.intellij.ide.vfs.rpcId
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.util.concurrency.AppExecutorUtil
import dev.twango.jetplay.JetPlayBundle
import dev.twango.jetplay.JetPlayConstants
import dev.twango.jetplay.browser.PlayerBridge
import dev.twango.jetplay.browser.PlayerConfig
import dev.twango.jetplay.browser.PlayerHtmlLoader
import dev.twango.jetplay.browser.UiStrings
import dev.twango.jetplay.media.EditorMediaSource
import dev.twango.jetplay.media.MediaClassification
import dev.twango.jetplay.media.MediaServer
import dev.twango.jetplay.rpc.MediaAccessor
import dev.twango.jetplay.rpc.TranscodeEvent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class MediaLoader(
    private val project: Project,
    private val source: EditorMediaSource,
    private val bridge: PlayerBridge,
    private val htmlLoader: PlayerHtmlLoader,
) {

    private val tasks = CopyOnWriteArrayList<Future<*>>()

    // Loopback URLs to release on dispose.
    private val servedUrls = CopyOnWriteArrayList<String>()

    @Volatile
    private var watchdog: ScheduledFuture<*>? = null

    private fun serve(file: File): String = MediaServer.serve(file).also { servedUrls.add(it) }

    /** JCEF may never reach the loopback server; error out if the token is unfetched by the deadline. */
    private fun armLoadWatchdog(url: String) {
        watchdog?.cancel(false)
        watchdog = AppExecutorUtil.getAppScheduledExecutorService().schedule({
            if (bridge.disposed || MediaServer.wasFetched(url)) return@schedule
            log.warn("Media load watchdog: $url served but never fetched after ${LOAD_TIMEOUT_SECONDS}s")
            bridge.showError(JetPlayBundle.message("error.load.timeout"))
        }, LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private val uiStrings = UiStrings(
        downloadingLabel = JetPlayBundle.message("ui.downloading.label"),
        transcodingLabel = JetPlayBundle.message("ui.transcoding.label"),
        transcodingTip = JetPlayBundle.message("ui.transcoding.tip"),
        errorTitle = JetPlayBundle.message("ui.error.title"),
    )

    private val fileId by lazy { source.file.rpcId() }
    private val projectId by lazy { project.projectId() }

    fun load() {
        // Transcoding runs backend-side and re-reads the source, so it takes precedence over download.
        when {
            source.needsTranscoding -> startTranscoding()
            source.isRemote -> startDownload()
            else -> playDirectly()
        }
        maybeSendWaveform()
        maybeSendMediaInfo()
    }

    private fun maybeSendWaveform() {
        if (source.isVideo || source.isRemote) return
        // Raw telephony codecs lack the demuxer hints to decode cleanly, risking a garbage waveform.
        if (source.extension.lowercase() in MediaClassification.rawAudioExtensions) return
        submit {
            if (bridge.disposed) return@submit
            val bars = runBlocking { MediaAccessor.getInstance().extractWaveform(fileId, projectId) }
            if (bars.isNotEmpty() && !bridge.disposed) bridge.sendWaveform(bars)
        }
    }

    private fun maybeSendMediaInfo() {
        if (source.isRemote) return
        if (source.extension.lowercase() in MediaClassification.rawAudioExtensions) return
        submit {
            if (bridge.disposed) return@submit
            val info = runBlocking { MediaAccessor.getInstance().extractMediaInfo(fileId, projectId) }
            if (info != null && !bridge.disposed) bridge.sendMediaInfo(info)
        }
    }

    private fun startDownload() {
        htmlLoader.load(
            PlayerConfig(
                state = "downloading",
                isVideo = source.isVideo,
                fileName = source.fileName,
                fileExtension = source.extension,
                downloadingReason = JetPlayBundle.message("downloading.reason"),
                ui = uiStrings,
            ),
        )
        submit {
            val temp = streamToTemp(::reportDownloadProgress) ?: return@submit
            if (bridge.disposed) {
                temp.delete()
                return@submit
            }
            val url = serve(temp)
            bridge.mediaReady(url)
            armLoadWatchdog(url)
        }
    }

    private fun startTranscoding() {
        // No prior page exists yet, so render the loading shell directly instead of pushing a state change.
        htmlLoader.load(
            PlayerConfig(
                state = "loading",
                isVideo = source.isVideo,
                fileName = source.fileName,
                fileExtension = source.extension,
                transcodingReason = JetPlayBundle.message("transcoding.reason", source.extension.uppercase()),
                ui = uiStrings,
            ),
        )
        submit { runTranscode() }
    }

    private fun runTranscode() {
        val temp = File.createTempFile("jetplay-", ".webm").apply { deleteOnExit() }
        try {
            runBlocking {
                val api = MediaAccessor.getInstance()
                temp.outputStream().use { out ->
                    api.transcodeFile(fileId, projectId).collect { event ->
                        when (event) {
                            is TranscodeEvent.Progress -> if (!bridge.disposed) bridge.updateProgress(event.percent)
                            is TranscodeEvent.Chunk -> out.write(event.bytes)
                            is TranscodeEvent.Failed -> throw TranscodeFailure(event.message)
                            TranscodeEvent.Unavailable -> throw TranscodeUnavailable
                            TranscodeEvent.Done -> Unit
                        }
                    }
                }
            }
            if (bridge.disposed) {
                temp.delete()
            } else {
                val url = serve(temp)
                bridge.mediaReady(url)
                armLoadWatchdog(url)
            }
        } catch (_: TranscodeUnavailable) {
            temp.delete()
            showTranscodingError()
        } catch (e: TranscodeFailure) {
            temp.delete()
            if (!bridge.disposed) bridge.showError(e.message ?: JetPlayBundle.message("error.unknown"))
        } catch (e: Exception) {
            temp.delete()
            showLoadError(e.message)
        }
    }

    private fun playDirectly() {
        val local = source.localFileOrNull()
        if (local != null) {
            val url = serve(local)
            htmlLoader.load(
                PlayerConfig(
                    isVideo = source.isVideo,
                    fileName = source.fileName,
                    fileExtension = source.extension,
                    mediaUrl = url,
                    ui = uiStrings,
                ),
            )
            armLoadWatchdog(url)
            return
        }
        // Show a loading shell up front so the streaming RPC doesn't leave the tab on a blank page.
        htmlLoader.load(
            PlayerConfig(
                state = "loading",
                isVideo = source.isVideo,
                fileName = source.fileName,
                fileExtension = source.extension,
                ui = uiStrings,
            ),
        )
        submit {
            val temp = streamToTemp { } ?: return@submit
            if (bridge.disposed) {
                temp.delete()
            } else {
                val url = serve(temp)
                htmlLoader.load(
                    PlayerConfig(
                        isVideo = source.isVideo,
                        fileName = source.fileName,
                        fileExtension = source.extension,
                        mediaUrl = url,
                        ui = uiStrings,
                    ),
                )
                armLoadWatchdog(url)
            }
        }
    }

    /** Streams the source bytes from the backend into a temp file. Returns null on failure (error surfaced). */
    private fun streamToTemp(onProgress: (Long) -> Unit): File? {
        val temp = File.createTempFile("jetplay-", ".${source.extension}").apply { deleteOnExit() }
        val written = try {
            runBlocking {
                val api = MediaAccessor.getInstance()
                var bytes = 0L
                temp.outputStream().use { out ->
                    api.streamFileBytes(fileId, projectId).collect { chunk ->
                        out.write(chunk)
                        bytes += chunk.size
                        onProgress(bytes)
                    }
                }
                bytes
            }
        } catch (e: Exception) {
            temp.delete()
            showLoadError(e.message)
            return null
        }
        // Backend emits an empty flow when it can't resolve the file; error out instead of serving 0 bytes.
        if (written == 0L) {
            temp.delete()
            showLoadError(JetPlayBundle.message("error.empty"))
            return null
        }
        return temp
    }

    private fun reportDownloadProgress(bytes: Long) {
        if (bridge.disposed) return
        val total = source.file.length
        if (total > 0) bridge.updateDownloadProgress(bytes.toDouble() / total * PERCENT_SCALE)
    }

    private fun showLoadError(raw: String?) {
        val msg = raw ?: JetPlayBundle.message("error.unknown")
        log.warn("media load failed: $msg")
        if (bridge.disposed) return
        bridge.showError(JetPlayBundle.message("error.download", msg))
    }

    private fun showTranscodingError() {
        // No page exists yet, so load the shell in the error state rather than pushing showError over JS.
        htmlLoader.load(
            PlayerConfig(
                state = "error",
                isVideo = source.isVideo,
                fileName = source.fileName,
                fileExtension = source.extension,
                errorMessage = JetPlayBundle.message("error.transcoding.message"),
                ui = uiStrings,
            ),
        )
        NotificationGroupManager.getInstance()
            .getNotificationGroup(JetPlayConstants.NOTIFICATION_GROUP_ID)
            .createNotification(
                JetPlayBundle.message("error.transcoding.notification.title"),
                JetPlayBundle.message("error.transcoding.notification.content", source.extension.uppercase()),
                NotificationType.WARNING,
            )
            .addAction(
                NotificationAction.createSimpleExpiring(JetPlayBundle.message("action.report.issue")) {
                    BrowserUtil.browse(JetPlayConstants.ISSUES_URL)
                },
            )
            .notify(project)
    }

    private fun submit(block: () -> Unit) {
        tasks.add(ApplicationManager.getApplication().executeOnPooledThread(block))
    }

    fun dispose() {
        watchdog?.cancel(false)
        tasks.forEach { it.cancel(true) }
        servedUrls.forEach(MediaServer::release)
    }

    private object TranscodeUnavailable : Exception()
    private class TranscodeFailure(message: String) : Exception(message)

    companion object {
        private val log = Logger.getInstance(MediaLoader::class.java)
        private const val PERCENT_SCALE = 100.0
        private const val LOAD_TIMEOUT_SECONDS = 20L
    }
}

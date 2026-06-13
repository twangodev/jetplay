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

class MediaLoader(
    private val project: Project,
    private val source: EditorMediaSource,
    private val bridge: PlayerBridge,
    private val htmlLoader: PlayerHtmlLoader,
) {

    private val tasks = CopyOnWriteArrayList<Future<*>>()

    // Loopback URLs handed out for this editor's media, released on dispose.
    private val servedUrls = CopyOnWriteArrayList<String>()

    private fun serve(file: File): String = MediaServer.serve(file).also { servedUrls.add(it) }

    private val uiStrings = UiStrings(
        downloadingLabel = JetPlayBundle.message("ui.downloading.label"),
        transcodingLabel = JetPlayBundle.message("ui.transcoding.label"),
        transcodingTip = JetPlayBundle.message("ui.transcoding.tip"),
        errorTitle = JetPlayBundle.message("ui.error.title"),
    )

    private val fileId by lazy { source.file.rpcId() }
    private val projectId by lazy { project.projectId() }

    fun load() {
        when {
            source.isRemote -> startDownload()
            source.needsTranscoding -> startTranscoding()
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
            if (bridge.disposed) return@submit
            if (source.needsTranscoding) {
                startTranscoding()
            } else {
                bridge.mediaReady(serve(temp))
            }
        }
    }

    private fun startTranscoding() {
        if (source.isRemote) {
            bridge.executeJs("window.__jetplayState='loading';window.__jetplayProgress=0;window.jetplayStartTranscoding?.()")
        } else {
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
        }
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
            if (!bridge.disposed) bridge.mediaReady(serve(temp))
        } catch (_: TranscodeUnavailable) {
            showTranscodingError()
        } catch (e: TranscodeFailure) {
            if (!bridge.disposed) bridge.showError(e.message ?: JetPlayBundle.message("error.unknown"))
        } catch (e: Exception) {
            showLoadError(e.message)
        }
    }

    private fun playDirectly() {
        val local = source.localFileOrNull()
        if (local != null) {
            // MONOLITH / local file: identical to today — serve the real file, no RPC, no temp copy.
            htmlLoader.load(
                PlayerConfig(
                    isVideo = source.isVideo,
                    fileName = source.fileName,
                    fileExtension = source.extension,
                    mediaUrl = serve(local),
                    ui = uiStrings,
                ),
            )
            return
        }
        // SPLIT MODE: pull bytes from backend into a temp file, then serve.
        submit {
            val temp = streamToTemp { } ?: return@submit
            if (!bridge.disposed) {
                htmlLoader.load(
                    PlayerConfig(
                        isVideo = source.isVideo,
                        fileName = source.fileName,
                        fileExtension = source.extension,
                        mediaUrl = serve(temp),
                        ui = uiStrings,
                    ),
                )
            }
        }
    }

    /** Streams the source bytes from the backend into a temp file. Returns null on failure (error surfaced). */
    private fun streamToTemp(onProgress: (Long) -> Unit): File? {
        val temp = File.createTempFile("jetplay-", ".${source.extension}").apply { deleteOnExit() }
        return try {
            runBlocking {
                val api = MediaAccessor.getInstance()
                var written = 0L
                temp.outputStream().use { out ->
                    api.streamFileBytes(fileId, projectId).collect { chunk ->
                        out.write(chunk)
                        written += chunk.size
                        onProgress(written)
                    }
                }
            }
            temp
        } catch (e: Exception) {
            showLoadError(e.message)
            null
        }
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
        // Load the shell in the error state: this runs before any page exists, so a
        // bridge.showError() JS push would have nothing to render against.
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
        tasks.forEach { it.cancel(true) }
        servedUrls.forEach(MediaServer::release)
    }

    private object TranscodeUnavailable : Exception()
    private class TranscodeFailure(message: String) : Exception(message)

    companion object {
        private val log = Logger.getInstance(MediaLoader::class.java)
        private const val PERCENT_SCALE = 100.0
    }
}

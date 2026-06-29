package dev.twango.jetplay.editor

import com.intellij.ide.BrowserUtil
import com.intellij.ide.vfs.rpcId
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import dev.twango.jetplay.JetPlayBundle
import dev.twango.jetplay.JetPlayConstants
import dev.twango.jetplay.browser.PlayerBridge
import dev.twango.jetplay.browser.PlayerConfig
import dev.twango.jetplay.browser.PlayerHtmlLoader
import dev.twango.jetplay.browser.UiStrings
import dev.twango.jetplay.media.EditorMediaSource
import dev.twango.jetplay.media.MediaClassification
import dev.twango.jetplay.media.MediaServer
import dev.twango.jetplay.media.RemoteRangeByteSource
import dev.twango.jetplay.media.contentTypeForExtension
import dev.twango.jetplay.rpc.MediaAccessor
import dev.twango.jetplay.rpc.TranscodeEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

class MediaLoader(
    private val project: Project,
    private val source: EditorMediaSource,
    private val bridge: PlayerBridge,
    private val htmlLoader: PlayerHtmlLoader,
) {

    private val scope = project.service<MediaCoroutineService>().scope.childScope("MediaLoader")

    // Loopback URLs to release on dispose.
    private val servedUrls = CopyOnWriteArrayList<String>()

    // Transcode outputs to delete on dispose rather than leaking until JVM exit.
    private val servedTempFiles = CopyOnWriteArrayList<File>()

    @Volatile
    private var disposed = false

    @Volatile
    private var watchdog: Job? = null

    // null if disposal already released the URL (lost the race with dispose).
    private fun registerServed(url: String): String? {
        servedUrls.add(url)
        if (disposed) {
            MediaServer.release(url)
            return null
        }
        return url
    }

    private fun armLoadWatchdog(url: String) {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(LOAD_TIMEOUT_SECONDS * MILLIS_PER_SECOND)
            if (bridge.disposed || MediaServer.wasFetched(url)) return@launch
            // A backgrounded tab never loads its JCEF page, so it never fetches; only flag a stall the user can see.
            if (!bridge.isShowing()) return@launch
            log.warn("Media load watchdog: $url served but never fetched after ${LOAD_TIMEOUT_SECONDS}s")
            bridge.showError(JetPlayBundle.message("error.load.timeout"))
        }
    }

    private val uiStrings = UiStrings(
        transcodingLabel = JetPlayBundle.message("ui.transcoding.label"),
        transcodingTip = JetPlayBundle.message("ui.transcoding.tip"),
        errorTitle = JetPlayBundle.message("ui.error.title"),
    )

    private val fileId by lazy { source.file.rpcId() }
    private val projectId by lazy { project.projectId() }

    // Guards the one-shot lazy spectrogram compute against duplicate reveal requests.
    private val spectrogramRequested = java.util.concurrent.atomic.AtomicBoolean(false)

    fun load() {
        when {
            source.needsTranscoding -> startTranscoding()
            else -> playFromSource()
        }
        maybeSendWaveform()
        maybeSendMediaInfo()
        wireSpectrogram()
    }

    private fun wireSpectrogram() {
        // Video uses the video player, which has no spectrogram lane.
        if (source.isVideo) return
        bridge.onSpectrogramRequest = { requestSpectrogram() }
    }

    // Computed on first reveal rather than on open: a full STFT decode is too heavy to run for every audio file.
    private fun requestSpectrogram() {
        if (!spectrogramRequested.compareAndSet(false, true)) return
        if (source.isRemote || source.extension.lowercase() in MediaClassification.rawAudioExtensions) {
            if (!bridge.disposed) bridge.sendSpectrogram(null)
            return
        }
        scope.launch {
            val spec = MediaAccessor.getInstance().extractSpectrogram(fileId, projectId)
            if (!bridge.disposed) bridge.sendSpectrogram(spec)
        }
    }

    private fun maybeSendWaveform() {
        if (source.isVideo || source.isRemote) return
        // Raw telephony codecs lack the demuxer hints to decode cleanly, risking a garbage waveform.
        if (source.extension.lowercase() in MediaClassification.rawAudioExtensions) return
        scope.launch {
            val bars = MediaAccessor.getInstance().extractWaveform(fileId, projectId)
            if (bars.isNotEmpty() && !bridge.disposed) bridge.sendWaveform(bars)
        }
    }

    private fun maybeSendMediaInfo() {
        if (source.isRemote) return
        if (source.extension.lowercase() in MediaClassification.rawAudioExtensions) return
        scope.launch {
            val info = MediaAccessor.getInstance().extractMediaInfo(fileId, projectId)
            if (info != null && !bridge.disposed) bridge.sendMediaInfo(info)
        }
    }

    private fun playFromSource() {
        val local = source.localFileOrNull()
        if (local != null) {
            val url = registerServed(MediaServer.serve(local)) ?: return
            loadPlayer(url)
            return
        }
        htmlLoader.load(
            PlayerConfig(
                state = "loading",
                isVideo = source.isVideo,
                fileName = source.fileName,
                fileExtension = source.extension,
                ui = uiStrings,
            ),
        )
        scope.launch {
            val len = MediaAccessor.getInstance().fileLength(fileId, projectId)
            if (len <= 0L) {
                showLoadError(JetPlayBundle.message("error.empty"))
                return@launch
            }
            // The HTTP server thread calls this reader synchronously, so it bridges the suspend RPC with runBlocking.
            // Parent on the loader scope so editor dispose cancels in-flight reads; bound each read so a stalled link fails fast.
            val readerJob = scope.coroutineContext.job
            val remote = RemoteRangeByteSource(len, contentTypeForExtension(source.extension)) { offset, length ->
                runBlocking(readerJob) {
                    withTimeout(RPC_READ_TIMEOUT) {
                        MediaAccessor.getInstance().readRange(fileId, projectId, offset, length)
                    }
                }
            }
            val url = registerServed(MediaServer.serve(remote)) ?: return@launch
            // Push the URL in-page rather than a second loadHTML, which would race the shell's load.
            bridge.mediaReady(url)
            armLoadWatchdog(url)
        }
    }

    private fun loadPlayer(url: String) {
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
        scope.launch { runTranscode() }
    }

    private suspend fun runTranscode() {
        val temp = File.createTempFile("jetplay-", ".webm").apply { deleteOnExit() }
        var served = false
        try {
            val api = MediaAccessor.getInstance()
            temp.outputStream().use { out ->
                api.transcodeFile(fileId, projectId).collect { event -> writeTranscodeEvent(event, out) }
            }
            if (!bridge.disposed) {
                val url = registerServed(MediaServer.serve(temp))
                if (url != null) {
                    served = true
                    servedTempFiles.add(temp)
                    bridge.mediaReady(url)
                    armLoadWatchdog(url)
                }
            }
        } catch (_: TranscodeUnavailable) {
            showTranscodingError()
        } catch (e: TranscodeFailure) {
            if (!bridge.disposed) bridge.showError(e.message ?: JetPlayBundle.message("error.unknown"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showLoadError(e.message)
        } finally {
            // Unserved temps drop now; served ones are deleted on dispose() when the editor closes.
            if (!served) temp.delete()
        }
    }

    private fun writeTranscodeEvent(event: TranscodeEvent, out: java.io.OutputStream) {
        when (event) {
            is TranscodeEvent.Progress -> if (!bridge.disposed) bridge.updateProgress(event.percent)
            is TranscodeEvent.Chunk -> out.write(event.bytes)
            is TranscodeEvent.Failed -> throw TranscodeFailure(event.message)
            TranscodeEvent.Unavailable -> throw TranscodeUnavailable
            TranscodeEvent.Done -> Unit
        }
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

    fun dispose() {
        disposed = true
        scope.cancel()
        servedUrls.forEach(MediaServer::release)
        servedTempFiles.forEach(File::delete)
    }

    private object TranscodeUnavailable : Exception()
    private class TranscodeFailure(message: String) : Exception(message)

    companion object {
        private val log = Logger.getInstance(MediaLoader::class.java)
        private const val LOAD_TIMEOUT_SECONDS = 20L
        private const val MILLIS_PER_SECOND = 1000L
        private val RPC_READ_TIMEOUT = 30.seconds
    }
}

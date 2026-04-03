package dev.twango.jetplay.editor

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import dev.twango.jetplay.JetPlayBundle
import dev.twango.jetplay.JetPlayConstants
import dev.twango.jetplay.browser.PlayerBridge
import dev.twango.jetplay.browser.PlayerConfig
import dev.twango.jetplay.browser.PlayerHtmlLoader
import dev.twango.jetplay.browser.UiStrings
import dev.twango.jetplay.media.MediaSource
import dev.twango.jetplay.media.RemoteFileMediaSource
import dev.twango.jetplay.transcode.FfmpegAvailability
import dev.twango.jetplay.transcode.TranscodeSession
import dev.twango.jetplay.transfer.DownloadSession
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

class MediaFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val source: MediaSource
) : UserDataHolderBase(), FileEditor {

    private val browser = JBCefBrowser()
    private val bridge = PlayerBridge(browser)
    private val htmlLoader = PlayerHtmlLoader(bridge)
    private var downloadSession: DownloadSession? = null
    private var transcodeSession: TranscodeSession? = null
    private val uiStrings = UiStrings(
        downloadingLabel = JetPlayBundle.message("ui.downloading.label"),
        transcodingLabel = JetPlayBundle.message("ui.transcoding.label"),
        transcodingTip = JetPlayBundle.message("ui.transcoding.tip"),
        errorTitle = JetPlayBundle.message("ui.error.title")
    )

    private val component: JComponent = JPanel(BorderLayout()).apply {
        add(browser.component, BorderLayout.CENTER)
    }

    init {
        if (source.isRemote) {
            startDownload()
        } else if (source.needsTranscoding) {
            startTranscoding()
        } else {
            playDirectly()
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
                ui = uiStrings
            )
        )
        downloadSession = DownloadSession(source as RemoteFileMediaSource, bridge) {
            if (source.needsTranscoding) {
                startTranscoding()
            } else {
                bridge.mediaReady(source.resolvePlayableUrl())
            }
        }.also { it.start() }
    }

    private fun startTranscoding() {
        if (!FfmpegAvailability.available) {
            showTranscodingError()
            return
        }
        if (source.isRemote) {
            bridge.executeJs("window.jetplayStartTranscoding?.()")
        } else {
            htmlLoader.load(
                PlayerConfig(
                    state = "loading",
                    isVideo = source.isVideo,
                    fileName = source.fileName,
                    fileExtension = source.extension,
                    transcodingReason = JetPlayBundle.message("transcoding.reason", source.extension.uppercase()),
                    ui = uiStrings
                )
            )
        }
        transcodeSession = TranscodeSession(source.toLocalFile(), bridge).also { it.start() }
    }

    private fun playDirectly() {
        htmlLoader.load(
            PlayerConfig(
                isVideo = source.isVideo,
                fileName = source.fileName,
                fileExtension = source.extension,
                mediaUrl = source.resolvePlayableUrl(),
                ui = uiStrings
            )
        )
    }

    private fun showTranscodingError() {
        bridge.showError(JetPlayBundle.message("error.transcoding.message"))
        NotificationGroupManager.getInstance()
            .getNotificationGroup(JetPlayConstants.NOTIFICATION_GROUP_ID)
            .createNotification(
                JetPlayBundle.message("error.transcoding.notification.title"),
                JetPlayBundle.message("error.transcoding.notification.content", source.extension.uppercase()),
                NotificationType.WARNING
            )
            .addAction(NotificationAction.createSimpleExpiring(JetPlayBundle.message("action.report.issue")) {
                BrowserUtil.browse(JetPlayConstants.ISSUES_URL)
            })
            .notify(project)
    }

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = component
    override fun getName(): String = JetPlayBundle.message("editor.name")
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file

    override fun dispose() {
        downloadSession?.cancel()
        transcodeSession?.cancel()
        bridge.dispose()
    }
}

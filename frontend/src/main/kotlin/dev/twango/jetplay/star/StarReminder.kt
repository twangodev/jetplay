package dev.twango.jetplay.star

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import dev.twango.jetplay.JetPlayBundle
import dev.twango.jetplay.JetPlayConstants
import java.time.LocalDate

object StarReminder {

    private const val DISMISSED_KEY = "jetplay.star.dismissed"
    private const val LAST_SHOWN_DAY_KEY = "jetplay.star.lastShownDay"

    fun maybeShow(project: Project) {
        val props = PropertiesComponent.getInstance()
        val today = LocalDate.now().toEpochDay()
        val lastShown = props.getValue(LAST_SHOWN_DAY_KEY)?.toLongOrNull()

        if (!StarReminderPolicy.shouldShow(props.getBoolean(DISMISSED_KEY, false), lastShown, today)) return

        props.setValue(LAST_SHOWN_DAY_KEY, today.toString())

        NotificationGroupManager.getInstance()
            .getNotificationGroup(JetPlayConstants.NOTIFICATION_GROUP_ID)
            .createNotification(
                JetPlayBundle.message("notification.star.title"),
                JetPlayBundle.message("notification.star.content"),
                NotificationType.INFORMATION,
            )
            .addAction(
                NotificationAction.createSimpleExpiring(JetPlayBundle.message("action.star.github")) {
                    dismissForever(props)
                    BrowserUtil.browse(JetPlayConstants.REPO_URL)
                },
            )
            .addAction(
                NotificationAction.createSimpleExpiring(JetPlayBundle.message("action.star.feedback")) {
                    BrowserUtil.browse(JetPlayConstants.ISSUES_URL)
                },
            )
            .addAction(
                NotificationAction.createSimpleExpiring(JetPlayBundle.message("action.star.dismiss")) {
                    dismissForever(props)
                },
            )
            .notify(project)
    }

    private fun dismissForever(props: PropertiesComponent) {
        props.setValue(DISMISSED_KEY, true, false)
    }
}

package com.aipos.madogit.notifications

import android.content.Context
import com.aipos.madogit.data.database.entities.GitHubNotificationEntity
import com.aipos.madogit.data.repository.NotificationPreferences

/**
 * Abstraction over system notification delivery so the sync engine can be exercised in
 * unit tests without touching the Android NotificationManager.
 */
interface NotificationDispatcher {
    fun post(notification: GitHubNotificationEntity, prefs: NotificationPreferences)

    /** Dismisses the system notification for an item that was read or deleted in the app. */
    fun cancel(notificationId: String)

    /** Dismisses every notification posted by the app (sign-out, mark all read, clear). */
    fun cancelAll()
}

/** Production dispatcher backed by [NotificationHelper]. */
class SystemNotificationDispatcher(context: Context) : NotificationDispatcher {
    private val appContext = context.applicationContext

    override fun post(notification: GitHubNotificationEntity, prefs: NotificationPreferences) {
        NotificationHelper.postNotification(appContext, notification, prefs)
    }

    override fun cancel(notificationId: String) {
        NotificationHelper.cancelNotification(appContext, notificationId)
    }

    override fun cancelAll() {
        NotificationHelper.cancelAll(appContext)
    }
}

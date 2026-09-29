package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.MainActivity
import com.example.R
import com.example.data.database.entities.GitHubNotificationEntity
import com.example.data.repository.NotificationPreferences

object NotificationHelper {
    const val CHANNEL_PR = "channel_pull_requests"
    const val CHANNEL_ISSUES = "channel_issues"
    const val CHANNEL_ACTIONS = "channel_actions"
    const val CHANNEL_RELEASES = "channel_releases"
    const val CHANNEL_ACTIVITY = "channel_repo_activity"

    private const val GROUP_GITHUB_NOTIFICATIONS = "group_github_notifications"
    private const val GROUP_SUMMARY_NOTIFICATION_ID = 9001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channels = listOf(
                NotificationChannel(
                    CHANNEL_PR,
                    "Pull Requests",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for new PRs, reviews requested, approvals, merges, and changes"
                    enableLights(true)
                    lightColor = Color.GREEN
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_ISSUES,
                    "Issues & Mentions",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for new issues, assignments, mentions, and issue comments"
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_ACTIONS,
                    "GitHub Actions",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for workflow runs (success, failure, cancellation)"
                    enableLights(true)
                    lightColor = Color.RED
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_RELEASES,
                    "Releases",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts when new releases or tags are published"
                    enableLights(true)
                    lightColor = Color.MAGENTA
                },
                NotificationChannel(
                    CHANNEL_ACTIVITY,
                    "Repository Activity",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "New commits, comments, and monitored repo activity"
                }
            )

            channels.forEach { notificationManager.createNotificationChannel(it) }
        }
    }

    fun shouldNotify(item: GitHubNotificationEntity, prefs: NotificationPreferences): Boolean {
        return when (item.category) {
            "PR" -> {
                if (!prefs.prMaster) return false
                when (item.eventType) {
                    "REVIEW_REQUESTED" -> prefs.prReviewRequested
                    "APPROVED" -> prefs.prApproved
                    "CHANGES_REQUESTED" -> prefs.prChangesRequested
                    "MERGED" -> prefs.prMerged
                    "CLOSED" -> prefs.prClosed
                    else -> prefs.prOpened
                }
            }
            "ISSUE" -> {
                if (!prefs.issueMaster) return false
                when (item.eventType) {
                    "ASSIGNED" -> prefs.issueAssigned
                    "MENTION" -> prefs.issueMentioned
                    "COMMENT" -> prefs.issueCommented
                    else -> prefs.issueOpened
                }
            }
            "WORKFLOW" -> {
                if (!prefs.actionMaster) return false
                when (item.actionState?.uppercase()) {
                    "FAILURE", "FAILED" -> prefs.actionFailed
                    "SUCCESS" -> prefs.actionSucceeded
                    "CANCELLED" -> prefs.actionCancelled
                    else -> prefs.actionFailed
                }
            }
            "RELEASE" -> {
                prefs.releaseMaster && prefs.releasePublished
            }
            else -> {
                if (!prefs.activityMaster) return false
                when (item.eventType) {
                    "COMMIT", "PUSH" -> prefs.activityCommits
                    "COMMENT" -> prefs.activityComments
                    else -> true
                }
            }
        }
    }

    fun postNotification(
        context: Context,
        notification: GitHubNotificationEntity,
        prefs: NotificationPreferences
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        if (!shouldNotify(notification, prefs)) {
            return
        }

        val channelId = when (notification.category) {
            "PR" -> CHANNEL_PR
            "ISSUE" -> CHANNEL_ISSUES
            "WORKFLOW" -> CHANNEL_ACTIONS
            "RELEASE" -> CHANNEL_RELEASES
            else -> CHANNEL_ACTIVITY
        }

        // Tap intent: opens the target GitHub URL in browser or app
        val targetUrl = if (notification.targetUrl.isNotBlank()) notification.targetUrl
                        else "https://github.com/${notification.repoFullName}"
        val targetUri = targetUrl.toUri()

        val viewIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notification.id.hashCode(),
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Main app launch intent as alternative action
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_id", notification.id)
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            (notification.id + "_app").hashCode(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("[${notification.repoFullName}] ${notification.title}")
            .setContentText(notification.body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${notification.body}\n\nAuthor: @${notification.author}")
                    .setSummaryText(notification.repoFullName)
            )
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Open in GitHub",
                contentPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_info_details,
                "View in App",
                appPendingIntent
            )
            .setGroup(if (prefs.groupingEnabled) GROUP_GITHUB_NOTIFICATIONS else null)
            .setPriority(
                if (notification.category == "WORKFLOW" || notification.category == "PR" || notification.category == "ISSUE")
                    NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )

        if (!prefs.soundEnabled) {
            builder.setSilent(true)
        }
        if (!prefs.vibrationEnabled) {
            builder.setVibrate(longArrayOf(0))
        }

        val notificationId = notification.id.hashCode()
        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(notificationId, builder.build())

            // If grouping enabled, post group summary notification
            if (prefs.groupingEnabled) {
                val summaryNotification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setStyle(NotificationCompat.InboxStyle().setSummaryText("GitHub Alerts"))
                    .setGroup(GROUP_GITHUB_NOTIFICATIONS)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .build()
                manager.notify(GROUP_SUMMARY_NOTIFICATION_ID, summaryNotification)
            }
        } catch (_: SecurityException) {
            // Handled safely if permission is revoked mid-flight
        }
    }
}


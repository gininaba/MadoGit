package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthState
import com.example.data.auth.TokenManager
import com.example.data.database.entities.GitHubNotificationEntity
import com.example.data.repository.NotificationPreferences
import com.example.data.repository.PreferencesRepository
import com.example.notifications.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MadoGit", appName)
    }

    @Test
    fun `notification filtering respects user preferences`() {
        val prefs = NotificationPreferences(
            prMaster = true,
            prReviewRequested = true,
            prOpened = false,
            actionMaster = true,
            actionFailed = true,
            actionSucceeded = false
        )

        val prReviewNotification = GitHubNotificationEntity(
            id = "test_1",
            eventType = "REVIEW_REQUESTED",
            category = "PR",
            repoFullName = "octocat/Hello-World",
            title = "PR review requested",
            body = "Review needed",
            author = "octocat",
            avatarUrl = null,
            targetUrl = "https://github.com/octocat/Hello-World/pull/1",
            timestamp = 1000L
        )

        val prOpenedNotification = GitHubNotificationEntity(
            id = "test_2",
            eventType = "OPENED",
            category = "PR",
            repoFullName = "octocat/Hello-World",
            title = "New PR opened",
            body = "Check this",
            author = "octocat",
            avatarUrl = null,
            targetUrl = "https://github.com/octocat/Hello-World/pull/2",
            timestamp = 2000L
        )

        val actionFailedNotification = GitHubNotificationEntity(
            id = "test_3",
            eventType = "WORKFLOW_FAILED",
            category = "WORKFLOW",
            repoFullName = "octocat/Hello-World",
            title = "Build #1 failed",
            body = "Branch main",
            author = "octocat",
            avatarUrl = null,
            targetUrl = "https://github.com/octocat/Hello-World/actions/runs/1",
            timestamp = 3000L,
            actionState = "FAILED"
        )

        val actionSucceededNotification = GitHubNotificationEntity(
            id = "test_4",
            eventType = "WORKFLOW_SUCCESS",
            category = "WORKFLOW",
            repoFullName = "octocat/Hello-World",
            title = "Build #2 passed",
            body = "Branch main",
            author = "octocat",
            avatarUrl = null,
            targetUrl = "https://github.com/octocat/Hello-World/actions/runs/2",
            timestamp = 4000L,
            actionState = "SUCCESS"
        )

        assertTrue(NotificationHelper.shouldNotify(prReviewNotification, prefs))
        assertFalse(NotificationHelper.shouldNotify(prOpenedNotification, prefs))
        assertTrue(NotificationHelper.shouldNotify(actionFailedNotification, prefs))
        assertFalse(NotificationHelper.shouldNotify(actionSucceededNotification, prefs))
    }

    @Test
    fun `token manager saves and clears auth correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tokenManager = TokenManager(context)

        tokenManager.clearAuth()
        assertEquals(AuthState.Unauthenticated, tokenManager.authState.value)

        tokenManager.saveOAuthConfiguration("test_client_id", "test_client_secret")
        assertEquals("test_client_id", tokenManager.getOAuthClientId())
        assertEquals("test_client_secret", tokenManager.getOAuthClientSecret())
    }

    @Test
    fun `preferences repository updates sync and notification settings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsRepo = PreferencesRepository(context)

        val currentPrefs = prefsRepo.notificationPrefs.value
        val updatedPrefs = currentPrefs.copy(soundEnabled = false, vibrationEnabled = false)
        prefsRepo.updateNotificationPreferences(updatedPrefs)

        assertFalse(prefsRepo.notificationPrefs.value.soundEnabled)
        assertFalse(prefsRepo.notificationPrefs.value.vibrationEnabled)
    }

    @Test
    fun `preferences repository updates dynamic color preference`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsRepo = PreferencesRepository(context)

        prefsRepo.setDynamicColor(true)
        assertTrue(prefsRepo.dynamicColor.value)

        prefsRepo.setDynamicColor(false)
        assertFalse(prefsRepo.dynamicColor.value)
    }

    @Test
    fun `notification filtering respects assigned issues preference`() {
        val prefs = NotificationPreferences(
            issueMaster = true,
            issueAssigned = true,
            issueOpened = false
        )

        val assignedIssueNotification = GitHubNotificationEntity(
            id = "issue_assigned_1",
            eventType = "ASSIGNED",
            category = "ISSUE",
            repoFullName = "octocat/Hello-World",
            title = "Assigned issue",
            body = "You were assigned",
            author = "octocat",
            avatarUrl = null,
            targetUrl = "https://github.com/octocat/Hello-World/issues/1",
            timestamp = 5000L,
            actionState = "ASSIGNED"
        )

        assertTrue(NotificationHelper.shouldNotify(assignedIssueNotification, prefs))

        val prefsDisallowing = prefs.copy(issueAssigned = false)
        assertFalse(NotificationHelper.shouldNotify(assignedIssueNotification, prefsDisallowing))
    }

    @Test
    fun `notification filtering respects releases preference`() {
        val prefs = NotificationPreferences(
            releaseMaster = true,
            releasePublished = true
        )

        val releaseNotification = GitHubNotificationEntity(
            id = "rel_1",
            eventType = "RELEASE_PUBLISHED",
            category = "RELEASE",
            repoFullName = "octocat/Hello-World",
            title = "Release v1.0.0",
            body = "Tag: v1.0.0",
            author = "octocat",
            avatarUrl = null,
            targetUrl = "https://github.com/octocat/Hello-World/releases/tag/v1.0.0",
            timestamp = 6000L,
            actionState = "PUBLISHED"
        )

        assertTrue(NotificationHelper.shouldNotify(releaseNotification, prefs))

        val prefsNoReleases = prefs.copy(releaseMaster = false)
        assertFalse(NotificationHelper.shouldNotify(releaseNotification, prefsNoReleases))
    }

    @Test
    fun `openExternalUrl does not crash on empty or null URL`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Should handle safely without throwing unhandled exceptions
        com.example.ui.components.openExternalUrl(context, "")
        com.example.ui.components.openExternalUrl(context, null)
        com.example.ui.components.openExternalUrl(context, "https://github.com")
    }
}

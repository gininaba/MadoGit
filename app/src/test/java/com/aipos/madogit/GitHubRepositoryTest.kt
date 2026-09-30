package com.aipos.madogit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aipos.madogit.data.auth.TokenManager
import com.aipos.madogit.data.database.AppDatabase
import com.aipos.madogit.data.database.entities.GitHubNotificationEntity
import com.aipos.madogit.data.repository.GitHubRepository
import com.aipos.madogit.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GitHubRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GitHubRepository
    private lateinit var tokenManager: TokenManager
    private lateinit var prefsRepo: PreferencesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tokenManager = TokenManager(context)
        prefsRepo = PreferencesRepository(context)
        repository = GitHubRepository(db, tokenManager, prefsRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `parseIsoDate parses UTC format correctly`() {
        val iso = "2026-09-29T12:00:00Z"
        val millis = repository.parseIsoDate(iso)
        assertTrue(millis > 0)
    }

    @Test
    fun `parseIsoDate parses millisecond ISO format correctly`() {
        val iso = "2026-09-29T12:00:00.500Z"
        val millis = repository.parseIsoDate(iso)
        assertTrue(millis > 0)
    }

    @Test
    fun `parseIsoDate parses timezone offset format correctly`() {
        val iso = "2026-09-29T12:00:00+08:00"
        val millis = repository.parseIsoDate(iso)
        assertTrue(millis > 0)
    }

    @Test
    fun `parseIsoDate falls back safely on invalid string`() {
        val before = System.currentTimeMillis()
        val millis = repository.parseIsoDate("invalid-date-string")
        val after = System.currentTimeMillis()
        assertTrue(millis in before..after)
    }

    @Test
    fun `getAssistantSummary computes actionable items and triage breakdown accurately`() = runBlocking {
        val notifDao = db.notificationDao()

        // 1. Review requested PR
        notifDao.insert(
            GitHubNotificationEntity(
                id = "pr_1",
                eventType = "REVIEW_REQUESTED",
                category = "PR",
                repoFullName = "owner/repo",
                title = "PR #1",
                body = "Review needed",
                author = "dev1",
                avatarUrl = null,
                targetUrl = "https://github.com/owner/repo/pull/1",
                timestamp = 1000L,
                isRead = false,
                actionState = "REVIEW_REQUESTED"
            )
        )

        // 2. Assigned issue
        notifDao.insert(
            GitHubNotificationEntity(
                id = "issue_1",
                eventType = "ASSIGNED",
                category = "ISSUE",
                repoFullName = "owner/repo",
                title = "Issue #2",
                body = "You were assigned",
                author = "dev2",
                avatarUrl = null,
                targetUrl = "https://github.com/owner/repo/issues/2",
                timestamp = 2000L,
                isRead = false,
                actionState = "ASSIGNED"
            )
        )

        // 3. Failed CI workflow run
        notifDao.insert(
            GitHubNotificationEntity(
                id = "run_1",
                eventType = "WORKFLOW_FAILED",
                category = "WORKFLOW",
                repoFullName = "owner/repo",
                title = "Build #3",
                body = "Failed",
                author = "owner",
                avatarUrl = null,
                targetUrl = "https://github.com/owner/repo/actions/runs/3",
                timestamp = 3000L,
                isRead = false,
                actionState = "FAILED"
            )
        )

        // 4. Normal unread notification (e.g. general release)
        notifDao.insert(
            GitHubNotificationEntity(
                id = "rel_1",
                eventType = "RELEASE_PUBLISHED",
                category = "RELEASE",
                repoFullName = "owner/repo",
                title = "Release v1.0",
                body = "New release",
                author = "owner",
                avatarUrl = null,
                targetUrl = "https://github.com/owner/repo/releases/1",
                timestamp = 4000L,
                isRead = false,
                actionState = "PUBLISHED"
            )
        )

        val summary = repository.getAssistantSummary()

        assertEquals(1, summary.pendingReviewRequests)
        assertEquals(1, summary.assignedIssues)
        assertEquals(1, summary.failedWorkflows)
        assertEquals(4, summary.unreadNotifications)
        assertEquals(3, summary.totalActionableItems)
        assertEquals(3, summary.topActionableItems.size)
    }

    @Test
    fun `getNotificationsByCategory filters by category properly`() = runBlocking {
        val notifDao = db.notificationDao()
        notifDao.insert(
            GitHubNotificationEntity(
                id = "pr_1",
                eventType = "REVIEW_REQUESTED",
                category = "PR",
                repoFullName = "owner/repo",
                title = "PR",
                body = "",
                author = "dev",
                avatarUrl = null,
                targetUrl = "",
                timestamp = 1000L
            )
        )
        notifDao.insert(
            GitHubNotificationEntity(
                id = "issue_1",
                eventType = "ASSIGNED",
                category = "ISSUE",
                repoFullName = "owner/repo",
                title = "Issue",
                body = "",
                author = "dev",
                avatarUrl = null,
                targetUrl = "",
                timestamp = 2000L
            )
        )

        val prList = repository.getNotificationsByCategory("PR").first()
        assertEquals(1, prList.size)
        assertEquals("PR", prList[0].category)

        val allList = repository.getNotificationsByCategory("ALL").first()
        assertEquals(2, allList.size)
    }

    @Test
    fun `clearCache wipes notifications processed events and logs`() = runBlocking {
        val notifDao = db.notificationDao()
        notifDao.insert(
            GitHubNotificationEntity(
                id = "item_1",
                eventType = "TEST",
                category = "PR",
                repoFullName = "owner/repo",
                title = "Test",
                body = "",
                author = "dev",
                avatarUrl = null,
                targetUrl = "",
                timestamp = 1000L
            )
        )

        assertEquals(1, notifDao.getTotalNotificationCount())
        repository.clearCache()
        assertEquals(0, notifDao.getTotalNotificationCount())
    }
}




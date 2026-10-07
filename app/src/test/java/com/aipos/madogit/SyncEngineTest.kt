package com.aipos.madogit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aipos.madogit.data.auth.AuthState
import com.aipos.madogit.data.auth.CryptoManager
import com.aipos.madogit.data.auth.TokenManager
import com.aipos.madogit.data.database.AppDatabase
import com.aipos.madogit.data.database.entities.GitHubNotificationEntity
import com.aipos.madogit.data.database.entities.MonitoredRepoEntity
import com.aipos.madogit.data.database.entities.ProcessedEventEntity
import com.aipos.madogit.data.database.entities.SyncLogEntity
import com.aipos.madogit.data.repository.GitHubRepository
import com.aipos.madogit.data.repository.PreferencesRepository
import com.aipos.madogit.data.repository.SyncPreferences
import com.aipos.madogit.data.repository.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import javax.crypto.KeyGenerator

/**
 * Behavioural tests for the sync engine in [GitHubRepository], driven through a fake GitHub API.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var tokenManager: TokenManager
    private lateinit var prefsRepo: PreferencesRepository
    private lateinit var api: FakeGitHubApiService
    private lateinit var dispatcher: RecordingDispatcher
    private lateinit var repository: GitHubRepository

    @Before
    fun setUp() {
        CryptoManager.testSecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tokenManager = TokenManager(context)
        prefsRepo = PreferencesRepository(context)
        api = FakeGitHubApiService()
        dispatcher = RecordingDispatcher()
        repository = GitHubRepository(db, tokenManager, prefsRepo, dispatcher, api)
        tokenManager.saveAuthSuccess("ghp_test_token", api.user)
    }

    @After
    fun tearDown() {
        db.close()
        CryptoManager.testSecretKey = null
    }

    // ---------- helpers ----------

    private fun repoEntity(id: Long, monitored: Boolean, lastSynced: Long = 0L, fullName: String = "octo/repo$id") =
        MonitoredRepoEntity(
            id = id, fullName = fullName, name = fullName.substringAfter('/'), owner = fullName.substringBefore('/'),
            isPrivate = false, description = null, stargazersCount = 0, defaultBranch = "main",
            htmlUrl = "https://github.com/$fullName", isMonitored = monitored, lastSyncedAt = lastSynced
        )

    /** Marks the database as having completed at least one sync, so new events are announced. */
    private suspend fun seedPriorSync() {
        db.syncLogDao().insertLog(SyncLogEntity(status = "SUCCESS", itemsFound = 0, newNotificationsCount = 0))
    }

    // ---------- deduplication ----------

    @Test
    fun `distinct threads sharing a fallback repo url survive repeated syncs`() = runBlocking {
        seedPriorSync()
        api.notifications = listOf(
            FakeGitHubApiService.thread("a", "CheckSuite", subjectUrl = null),
            FakeGitHubApiService.thread("b", "CheckSuite", subjectUrl = null)
        )

        repository.syncAll()
        repository.syncAll()

        assertNotNull(db.notificationDao().getNotificationById("gh_thread_a"))
        assertNotNull(db.notificationDao().getNotificationById("gh_thread_b"))
    }

    // ---------- repository refresh ----------

    @Test
    fun `refresh preserves explicit unmonitor choice when monitor-all default is on`() = runBlocking {
        prefsRepo.updateSyncPreferences(SyncPreferences(monitorAllByDefault = true))
        db.repoDao().insertIgnore(listOf(repoEntity(1, monitored = false), repoEntity(2, monitored = true, lastSynced = 777L)))
        api.repos = listOf(FakeGitHubApiService.repo(1), FakeGitHubApiService.repo(2), FakeGitHubApiService.repo(3))

        repository.refreshRepositories()

        assertFalse(db.repoDao().getRepoById(1)!!.isMonitored)
        assertEquals(777L, db.repoDao().getRepoById(2)!!.lastSyncedAt)
        assertTrue(db.repoDao().getRepoById(3)!!.isMonitored)
    }

    @Test
    fun `unmonitor all is not undone by a later refresh`() = runBlocking {
        db.repoDao().insertIgnore(listOf(repoEntity(1, monitored = false), repoEntity(2, monitored = false)))
        api.repos = listOf(FakeGitHubApiService.repo(1), FakeGitHubApiService.repo(2))

        repository.refreshRepositories()

        assertTrue(db.repoDao().getMonitoredReposSync().isEmpty())
    }

    @Test
    fun `first import auto-monitors a starter set of repositories`() = runBlocking {
        api.repos = (1L..5L).map { FakeGitHubApiService.repo(it) }

        repository.refreshRepositories()

        assertEquals(3, db.repoDao().getMonitoredReposSync().size)
    }

    @Test
    fun `refresh paginates beyond the first hundred repositories`() = runBlocking {
        api.repos = (1L..105L).map { FakeGitHubApiService.repo(it) }

        repository.refreshRepositories()

        assertEquals(105, db.repoDao().getRepoCount())
        assertEquals(listOf(1, 2), api.repoPagesRequested.toList())
    }

    @Test
    fun `refresh removes repositories that are no longer accessible`() = runBlocking {
        db.repoDao().insertIgnore(listOf(repoEntity(99, monitored = true)))
        api.repos = listOf(FakeGitHubApiService.repo(1))

        repository.refreshRepositories()

        assertNull(db.repoDao().getRepoById(99))
        assertNotNull(db.repoDao().getRepoById(1))
    }

    // ---------- notification delivery ----------

    @Test
    fun `first sync imports existing unread threads without posting system notifications`() = runBlocking {
        api.notifications = listOf(
            FakeGitHubApiService.thread("1", "Issue", "https://api.github.com/repos/octo/repo1/issues/1"),
            FakeGitHubApiService.thread("2", "Issue", "https://api.github.com/repos/octo/repo1/issues/2")
        )

        repository.syncAll()

        assertTrue(dispatcher.posted.isEmpty())
        assertEquals(2, db.notificationDao().getUnreadNotifications().size)
    }

    @Test
    fun `subsequent sync posts only newly discovered threads`() = runBlocking {
        api.notifications = listOf(FakeGitHubApiService.thread("1", "Issue", "https://api.github.com/repos/octo/repo1/issues/1"))
        repository.syncAll()

        api.notifications = api.notifications + FakeGitHubApiService.thread("3", "Issue", "https://api.github.com/repos/octo/repo1/issues/3")
        repository.syncAll()

        assertEquals(listOf("gh_thread_3"), dispatcher.posted.map { it.id })
    }

    @Test
    fun `thread with new activity after being read resurfaces as unread`() = runBlocking {
        seedPriorSync()
        api.notifications = listOf(FakeGitHubApiService.thread("5", "Issue", "https://api.github.com/repos/octo/repo1/issues/5"))
        repository.syncAll()
        repository.markNotificationAsRead("gh_thread_5")

        api.notifications = listOf(
            FakeGitHubApiService.thread(
                "5", "Issue", "https://api.github.com/repos/octo/repo1/issues/5",
                title = "Thread 5 (edited)", updatedAt = "2026-10-02T10:00:00Z"
            )
        )
        repository.syncAll()

        val stored = db.notificationDao().getNotificationById("gh_thread_5")!!
        assertFalse(stored.isRead)
        assertEquals("Thread 5 (edited)", stored.title)
        assertEquals(listOf("gh_thread_5", "gh_thread_5"), dispatcher.posted.map { it.id })
    }

    @Test
    fun `thread read on github is marked read locally`() = runBlocking {
        seedPriorSync()
        api.notifications = listOf(FakeGitHubApiService.thread("6", "Issue", "https://api.github.com/repos/octo/repo1/issues/6"))
        repository.syncAll()

        api.notifications = listOf(FakeGitHubApiService.thread("6", "Issue", "https://api.github.com/repos/octo/repo1/issues/6", unread = false))
        repository.syncAll()

        assertTrue(db.notificationDao().getNotificationById("gh_thread_6")!!.isRead)
        assertEquals(listOf("gh_thread_6"), dispatcher.cancelled.toList())
    }

    @Test
    fun `reading or deleting an item dismisses its system notification`() = runBlocking {
        db.notificationDao().insert(GitHubNotificationEntity("gh_thread_7", "TEST", "PR", "octo/repo1", "t", "", "me", null, "", 1L))
        db.notificationDao().insert(GitHubNotificationEntity("gh_pr_8", "TEST", "PR", "octo/repo1", "t2", "", "me", null, "", 2L))

        repository.markNotificationAsRead("gh_thread_7")
        repository.deleteNotification("gh_pr_8")

        assertEquals(listOf("gh_thread_7", "gh_pr_8"), dispatcher.cancelled.toList())
        assertEquals(listOf("7"), api.markedThreads.toList())
    }

    @Test
    fun `overlapping sync requests run a single sweep`() = runBlocking {
        seedPriorSync()
        api.rateLimitDelayMs = 300

        listOf(
            async(Dispatchers.Default) { repository.syncAll() },
            async(Dispatchers.Default) { repository.syncAll() }
        ).awaitAll()

        assertEquals(1, api.notificationsCalls.get())
    }

    @Test
    fun `cancelled workflow runs are recorded when the preference is enabled`() = runBlocking {
        seedPriorSync()
        prefsRepo.updateNotificationPreferences(prefsRepo.notificationPrefs.value.copy(actionCancelled = true))
        db.repoDao().insertIgnore(listOf(repoEntity(1, monitored = true, lastSynced = 1L)))
        api.repos = listOf(FakeGitHubApiService.repo(1))
        api.workflowRuns = mapOf("octo/repo1" to listOf(FakeGitHubApiService.run(10, "cancelled")))

        repository.syncAll()

        val stored = db.notificationDao().getNotificationById("gh_run_10")
        assertNotNull(stored)
        assertEquals("CANCELLED", stored!!.actionState)
        assertEquals(listOf("gh_run_10"), dispatcher.posted.map { it.id })
    }

    @Test
    fun `pull request merged after being seen open is resurfaced as merged`() = runBlocking {
        seedPriorSync()
        db.repoDao().insertIgnore(listOf(repoEntity(1, monitored = true, lastSynced = 1L)))
        api.repos = listOf(FakeGitHubApiService.repo(1))
        api.pullRequests = mapOf("octo/repo1" to listOf(FakeGitHubApiService.pr(42)))
        repository.syncAll()
        repository.markNotificationAsRead("gh_pr_42")

        api.pullRequests = mapOf(
            "octo/repo1" to listOf(FakeGitHubApiService.pr(42, state = "closed", mergedAt = "2026-10-02T09:00:00Z"))
        )
        repository.syncAll()

        val stored = db.notificationDao().getNotificationById("gh_pr_42")!!
        assertEquals("MERGED", stored.eventType)
        assertEquals("MERGED", stored.actionState)
        assertFalse(stored.isRead)
        assertEquals(listOf("gh_pr_42", "gh_pr_42"), dispatcher.posted.map { it.id })
        assertEquals("all", api.lastPullRequestState)
    }

    @Test
    fun `pull request closed without merge is recorded as closed`() = runBlocking {
        seedPriorSync()
        db.repoDao().insertIgnore(listOf(repoEntity(1, monitored = true, lastSynced = 1L)))
        api.repos = listOf(FakeGitHubApiService.repo(1))
        api.pullRequests = mapOf("octo/repo1" to listOf(FakeGitHubApiService.pr(43, state = "closed")))

        repository.syncAll()
        repository.syncAll()

        val stored = db.notificationDao().getNotificationById("gh_pr_43")!!
        assertEquals("CLOSED", stored.eventType)
        assertEquals(listOf("gh_pr_43"), dispatcher.posted.map { it.id })
    }

    // ---------- rate limit protection ----------

    @Test
    fun `critically low quota suspends per-repository polling`() = runBlocking {
        db.repoDao().insertIgnore(listOf(repoEntity(1, monitored = true, lastSynced = 1L)))
        api.repos = listOf(FakeGitHubApiService.repo(1))
        api.rateRemaining = 50

        repository.syncAll()

        assertEquals(1, api.notificationsCalls.get())
        assertEquals(0, api.pullRequestCalls.get())
        assertEquals(0, api.workflowCalls.get())
    }

    @Test
    fun `conservative quota skips workflow polling but keeps pull requests`() = runBlocking {
        db.repoDao().insertIgnore(listOf(repoEntity(1, monitored = true, lastSynced = 1L)))
        api.repos = listOf(FakeGitHubApiService.repo(1))
        api.rateRemaining = 300

        repository.syncAll()

        assertEquals(1, api.pullRequestCalls.get())
        assertEquals(0, api.workflowCalls.get())
    }

    // ---------- auth / session ----------

    @Test
    fun `unauthorized sync clears the stored token`() = runBlocking {
        api.rateLimitError = HttpException(Response.error<Any>(401, "".toResponseBody()))

        repository.syncAll()

        assertNull(tokenManager.getAccessToken())
        assertTrue(tokenManager.authState.value is AuthState.Error)
    }

    @Test
    fun `sweep outcome marks revoked credentials as not retryable`() = runBlocking {
        api.rateLimitError = HttpException(Response.error<Any>(401, "".toResponseBody()))

        val outcome = repository.runSync()

        assertEquals(false, (outcome as SyncStatus.Error).retryable)
    }

    @Test
    fun `sweep outcome reports offline when the network is unreachable`() = runBlocking {
        api.rateLimitError = java.io.IOException("no route")

        assertTrue(repository.runSync() is SyncStatus.Offline)
        assertTrue(repository.isOffline.value)
    }

    @Test
    fun `disconnect wipes all data belonging to the previous account`() = runBlocking {
        db.repoDao().insertIgnore(listOf(repoEntity(1, monitored = true)))
        db.notificationDao().insert(
            GitHubNotificationEntity("n1", "TEST", "PR", "octo/repo1", "t", "", "me", null, "", 1L)
        )
        db.processedEventDao().insertProcessedEvent(ProcessedEventEntity("seed", "SEED", "octo/seed"))
        db.syncLogDao().insertLog(SyncLogEntity(status = "SUCCESS", itemsFound = 0, newNotificationsCount = 0))

        repository.disconnect()

        assertEquals(0, db.repoDao().getRepoCount())
        assertEquals(0, db.notificationDao().getTotalNotificationCount())
        assertEquals(0, db.processedEventDao().getProcessedCount())
        assertNull(db.syncLogDao().getLatestLog())
        assertEquals(AuthState.Unauthenticated, tokenManager.authState.value)
        assertEquals(1, dispatcher.cancelAllCalls.get())
    }

    // ---------- link mapping ----------

    @Test
    fun `thread api urls map to valid github html pages`() = runBlocking {
        api.notifications = listOf(
            FakeGitHubApiService.thread("c", "Commit", "https://api.github.com/repos/octo/repo1/commits/abc123"),
            FakeGitHubApiService.thread("r", "Release", "https://api.github.com/repos/octo/repo1/releases/555"),
            FakeGitHubApiService.thread("p", "PullRequest", "https://api.github.com/repos/octo/repo1/pulls/7")
        )

        repository.syncAll()

        val dao = db.notificationDao()
        assertEquals("https://github.com/octo/repo1/commit/abc123", dao.getNotificationById("gh_thread_c")!!.targetUrl)
        assertEquals("https://github.com/octo/repo1/releases", dao.getNotificationById("gh_thread_r")!!.targetUrl)
        assertEquals("https://github.com/octo/repo1/pull/7", dao.getNotificationById("gh_thread_p")!!.targetUrl)
    }

    // ---------- assistant ----------

    @Test
    fun `assistant summary counts timed out workflow runs as failures`() = runBlocking {
        db.notificationDao().insert(
            GitHubNotificationEntity(
                "run_t", "WORKFLOW_FAILED", "WORKFLOW", "octo/repo1", "CI #1: TIMED_OUT", "", "octo", null,
                "https://github.com/octo/repo1/actions/runs/1", 1L, isRead = false, actionState = "TIMED_OUT"
            )
        )

        assertEquals(1, repository.getAssistantSummary().failedWorkflows)
    }
}

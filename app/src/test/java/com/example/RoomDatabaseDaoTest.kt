package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.entities.GitHubNotificationEntity
import com.example.data.database.entities.MonitoredRepoEntity
import com.example.data.database.entities.ProcessedEventEntity
import com.example.data.database.entities.SyncLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDatabaseDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `repoDao insert and query monitored repos`() = runBlocking {
        val repoDao = db.repoDao()
        val repo1 = MonitoredRepoEntity(
            id = 1L,
            fullName = "owner/repo1",
            name = "repo1",
            owner = "owner",
            isPrivate = false,
            description = "Test repo 1",
            stargazersCount = 10,
            defaultBranch = "main",
            htmlUrl = "https://github.com/owner/repo1",
            isMonitored = true,
            lastSyncedAt = 1000L
        )
        val repo2 = MonitoredRepoEntity(
            id = 2L,
            fullName = "owner/repo2",
            name = "repo2",
            owner = "owner",
            isPrivate = true,
            description = "Test repo 2",
            stargazersCount = 5,
            defaultBranch = "main",
            htmlUrl = "https://github.com/owner/repo2",
            isMonitored = false,
            lastSyncedAt = 2000L
        )

        repoDao.insertIgnore(listOf(repo1, repo2))

        val monitored = repoDao.getMonitoredReposSync()
        assertEquals(1, monitored.size)
        assertEquals("owner/repo1", monitored[0].fullName)

        // Toggle repo 2 to monitored
        repoDao.setMonitored(2L, true)
        val updatedMonitored = repoDao.getMonitoredReposSync()
        assertEquals(2, updatedMonitored.size)

        // Test round-robin ordering (least recently synced first)
        val toSync = repoDao.getMonitoredReposToSync(limit = 1)
        assertEquals(1, toSync.size)
        assertEquals(1L, toSync[0].id)
    }

    @Test
    fun `notificationDao insert and mark as read flow`() = runBlocking {
        val notifDao = db.notificationDao()
        val notif1 = GitHubNotificationEntity(
            id = "notif_1",
            eventType = "REVIEW_REQUESTED",
            category = "PR",
            repoFullName = "owner/repo",
            title = "PR review needed",
            body = "Review PR 1",
            author = "dev1",
            avatarUrl = null,
            targetUrl = "https://github.com/owner/repo/pull/1",
            timestamp = 1000L,
            isRead = false,
            actionState = "REVIEW_REQUESTED"
        )
        val notif2 = GitHubNotificationEntity(
            id = "notif_2",
            eventType = "WORKFLOW_FAILED",
            category = "WORKFLOW",
            repoFullName = "owner/repo",
            title = "CI build failed",
            body = "Branch main failed",
            author = "dev2",
            avatarUrl = null,
            targetUrl = "https://github.com/owner/repo/actions/runs/2",
            timestamp = 2000L,
            isRead = false,
            actionState = "FAILED"
        )

        notifDao.insert(notif1)
        notifDao.insert(notif2)

        assertEquals(2, notifDao.getUnreadCountFlow().first())
        val unread = notifDao.getUnreadNotifications()
        assertEquals(2, unread.size)

        // Mark notif1 as read
        notifDao.markAsRead("notif_1")
        assertEquals(1, notifDao.getUnreadCountFlow().first())

        // Mark all as read
        notifDao.markAllAsRead()
        assertEquals(0, notifDao.getUnreadCountFlow().first())
    }

    @Test
    fun `processedEventDao deduplication works correctly`() = runBlocking {
        val eventDao = db.processedEventDao()
        val event = ProcessedEventEntity(
            eventId = "evt_12345",
            eventType = "PR",
            repoFullName = "owner/repo"
        )

        assertFalse(eventDao.isEventProcessed("evt_12345"))
        eventDao.insertProcessedEvent(event)
        assertTrue(eventDao.isEventProcessed("evt_12345"))

        // Duplicate insert should be ignored
        eventDao.insertProcessedEvent(event)
        assertEquals(1, eventDao.getProcessedCount())
    }

    @Test
    fun `syncLogDao inserts and retrieves latest logs`() = runBlocking {
        val logDao = db.syncLogDao()
        val log1 = SyncLogEntity(
            timestamp = 1000L,
            status = "SUCCESS",
            itemsFound = 3,
            newNotificationsCount = 2
        )
        val log2 = SyncLogEntity(
            timestamp = 2000L,
            status = "FAILED",
            itemsFound = 0,
            newNotificationsCount = 0,
            errorMessage = "Timeout"
        )

        logDao.insertLog(log1)
        logDao.insertLog(log2)

        val latest = logDao.getLatestLog()
        assertNotNull(latest)
        assertEquals("FAILED", latest?.status)

        val recent = logDao.getRecentLogsFlow(limit = 10).first()
        assertEquals(2, recent.size)
        assertEquals("FAILED", recent[0].status)
    }
}

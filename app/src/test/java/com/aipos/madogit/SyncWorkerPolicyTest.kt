package com.aipos.madogit

import androidx.work.ListenableWorker.Result
import com.aipos.madogit.data.repository.SyncStatus
import com.aipos.madogit.worker.GitHubSyncWorker
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncWorkerPolicyTest {

    @Test
    fun `successful or skipped sweeps succeed`() {
        assertEquals(Result.success(), GitHubSyncWorker.resultFor(SyncStatus.Success(2, 0L), 0))
        assertEquals(Result.success(), GitHubSyncWorker.resultFor(SyncStatus.Syncing, 0))
        assertEquals(Result.success(), GitHubSyncWorker.resultFor(SyncStatus.Idle, 0))
    }

    @Test
    fun `transient failures retry with backoff until the attempt budget is spent`() {
        assertEquals(Result.retry(), GitHubSyncWorker.resultFor(SyncStatus.Error("500"), 0))
        assertEquals(Result.retry(), GitHubSyncWorker.resultFor(SyncStatus.Offline("offline"), 2))
        assertEquals(Result.failure(), GitHubSyncWorker.resultFor(SyncStatus.Error("500"), 3))
        assertEquals(Result.success(), GitHubSyncWorker.resultFor(SyncStatus.Offline("offline"), 3))
    }

    @Test
    fun `revoked credentials never retry`() {
        assertEquals(Result.failure(), GitHubSyncWorker.resultFor(SyncStatus.Error("401", retryable = false), 0))
    }
}

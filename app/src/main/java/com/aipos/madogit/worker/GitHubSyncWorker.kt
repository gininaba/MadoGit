package com.aipos.madogit.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aipos.madogit.GitHubNotifierApp
import com.aipos.madogit.data.repository.SyncStatus

class GitHubSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background sync check...")
        val app = applicationContext as? GitHubNotifierApp ?: return Result.failure()

        // runSync() never throws for API/network failures; it reports them as its outcome.
        val outcome = app.repository.runSync()
        Log.d(TAG, "Background sync finished: $outcome (attempt ${runAttemptCount + 1})")
        return resultFor(outcome, runAttemptCount)
    }

    companion object {
        private const val TAG = "GitHubSyncWorker"
        const val WORK_NAME_PERIODIC = "github_periodic_sync_work"
        const val WORK_NAME_ONE_TIME = "github_one_time_sync_work"
        const val MAX_RETRIES = 3

        /**
         * Maps a sweep outcome to a WorkManager result. Transient failures are retried with the
         * request's exponential backoff; revoked credentials are never retried.
         */
        fun resultFor(outcome: SyncStatus, runAttemptCount: Int): Result = when (outcome) {
            is SyncStatus.Error ->
                if (outcome.retryable && runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            // Offline after retries is not an error: the next periodic run will try again.
            is SyncStatus.Offline ->
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}

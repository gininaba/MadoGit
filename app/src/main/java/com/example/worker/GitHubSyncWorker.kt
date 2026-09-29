package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.GitHubNotifierApp

class GitHubSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("GitHubSyncWorker", "Starting background sync check...")
        val app = applicationContext as? GitHubNotifierApp ?: return Result.failure()

        return try {
            val count = app.repository.syncAll(applicationContext)
            Log.d("GitHubSyncWorker", "Background sync completed with $count new notifications")
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("GitHubSyncWorker", "Background sync error", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "github_periodic_sync_work"
        const val WORK_NAME_ONE_TIME = "github_one_time_sync_work"
    }
}

package com.aipos.madogit.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkManagerScheduler {

    fun schedulePeriodicSync(
        context: Context,
        intervalMinutes: Long,
        wifiOnly: Boolean
    ) {
        try {
            val workManager = WorkManager.getInstance(context)

            if (intervalMinutes <= 0L) {
                // Manual only
                workManager.cancelUniqueWork(GitHubSyncWorker.WORK_NAME_PERIODIC)
                return
            }

            // WorkManager minimum periodic interval is 15 minutes
            val effectiveInterval = intervalMinutes.coerceAtLeast(15L)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<GitHubSyncWorker>(
                effectiveInterval, TimeUnit.MINUTES,
                5L, TimeUnit.MINUTES // 5 min flex interval
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                GitHubSyncWorker.WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWorkRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("WorkManagerScheduler", "Failed to schedule periodic sync: ${e.message}")
        }
    }

    fun triggerImmediateSync(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeWork = OneTimeWorkRequestBuilder<GitHubSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                GitHubSyncWorker.WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                oneTimeWork
            )
        } catch (e: Exception) {
            android.util.Log.e("WorkManagerScheduler", "Failed to trigger immediate sync: ${e.message}")
        }
    }

    fun cancelAll(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(GitHubSyncWorker.WORK_NAME_PERIODIC)
        } catch (e: Exception) {
            android.util.Log.e("WorkManagerScheduler", "Failed to cancel work: ${e.message}")
        }
    }
}

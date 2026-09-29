package com.example

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.data.auth.TokenManager
import com.example.data.database.AppDatabase
import com.example.data.repository.GitHubRepository
import com.example.data.repository.PreferencesRepository
import com.example.notifications.NotificationHelper
import com.example.worker.WorkManagerScheduler

class GitHubNotifierApp : Application(), Configuration.Provider {

    lateinit var database: AppDatabase
        private set

    lateinit var tokenManager: TokenManager
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var repository: GitHubRepository
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        tokenManager = TokenManager(this)
        preferencesRepository = PreferencesRepository(this)
        repository = GitHubRepository(database, tokenManager, preferencesRepository)

        // Initialize Notification Channels
        NotificationHelper.createNotificationChannels(this)

        // Setup background WorkManager sync
        try {
            val syncPrefs = preferencesRepository.syncPrefs.value
            WorkManagerScheduler.schedulePeriodicSync(
                context = this,
                intervalMinutes = syncPrefs.intervalMinutes,
                wifiOnly = syncPrefs.wifiOnly
            )
        } catch (e: Exception) {
            Log.w("GitHubNotifierApp", "WorkManager initialization skipped or deferred: ${e.message}")
        }
    }
}

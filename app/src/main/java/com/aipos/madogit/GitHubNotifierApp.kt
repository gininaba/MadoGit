package com.aipos.madogit

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.aipos.madogit.data.auth.AuthState
import com.aipos.madogit.data.auth.TokenManager
import com.aipos.madogit.data.database.AppDatabase
import com.aipos.madogit.data.repository.GitHubRepository
import com.aipos.madogit.data.repository.PreferencesRepository
import com.aipos.madogit.notifications.NotificationHelper
import com.aipos.madogit.notifications.SystemNotificationDispatcher
import com.aipos.madogit.worker.WorkManagerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class GitHubNotifierApp : Application(), Configuration.Provider {

    lateinit var database: AppDatabase
        private set

    lateinit var tokenManager: TokenManager
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var repository: GitHubRepository
        private set

    /** Process-wide scope for work that must outlive any single screen (e.g. post-sign-in sync). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        com.aipos.madogit.data.api.ApiClient.initCache(cacheDir)
        database = AppDatabase.getInstance(this)
        tokenManager = TokenManager(this)
        preferencesRepository = PreferencesRepository(this)
        repository = GitHubRepository(
            database = database,
            tokenManager = tokenManager,
            preferencesRepository = preferencesRepository,
            notificationDispatcher = SystemNotificationDispatcher(this)
        )

        // Initialize Notification Channels
        NotificationHelper.createNotificationChannels(this)

        observeSessionForBackgroundSync()
    }

    /**
     * Background sync follows the session: scheduled while signed in, cancelled otherwise.
     * Previously work was only scheduled in onCreate, so signing out (which cancelled it) and back in
     * left the user with no background sync until the process was restarted.
     */
    private fun observeSessionForBackgroundSync() {
        applicationScope.launch {
            var wasSignedIn: Boolean? = null
            tokenManager.authState
                .map { it is AuthState.Authenticated }
                .distinctUntilChanged()
                .collect { signedIn ->
                    try {
                        if (signedIn) {
                            val syncPrefs = preferencesRepository.syncPrefs.value
                            WorkManagerScheduler.schedulePeriodicSync(
                                context = this@GitHubNotifierApp,
                                intervalMinutes = syncPrefs.intervalMinutes,
                                wifiOnly = syncPrefs.wifiOnly
                            )
                        } else {
                            WorkManagerScheduler.cancelAll(this@GitHubNotifierApp)
                        }
                    } catch (e: Exception) {
                        Log.w("GitHubNotifierApp", "WorkManager initialization skipped or deferred: ${e.message}")
                    }

                    // Fresh sign-in: populate the dashboard right away instead of waiting up to a full interval.
                    if (signedIn && wasSignedIn == false) {
                        launch { repository.syncAll() }
                    }
                    wasSignedIn = signedIn
                }
        }
    }
}

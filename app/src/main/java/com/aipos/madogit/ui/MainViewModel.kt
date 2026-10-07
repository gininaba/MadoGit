package com.aipos.madogit.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aipos.madogit.data.auth.AuthState
import com.aipos.madogit.data.auth.TokenManager
import com.aipos.madogit.data.database.entities.GitHubNotificationEntity
import com.aipos.madogit.data.database.entities.MonitoredRepoEntity
import com.aipos.madogit.data.repository.AssistantSummary
import com.aipos.madogit.data.repository.GitHubRepository
import com.aipos.madogit.data.repository.NotificationPreferences
import com.aipos.madogit.data.repository.PreferencesRepository
import com.aipos.madogit.data.repository.SyncPreferences
import com.aipos.madogit.data.repository.SyncStatus
import com.aipos.madogit.data.repository.ThemeMode
import com.aipos.madogit.ui.navigation.NavDestination
import com.aipos.madogit.worker.WorkManagerScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: GitHubRepository,
    private val tokenManager: TokenManager,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = tokenManager.authState
    val isOffline: StateFlow<Boolean> = repository.isOffline
    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus
    val notificationPrefs: StateFlow<NotificationPreferences> = preferencesRepository.notificationPrefs
    val syncPrefs: StateFlow<SyncPreferences> = preferencesRepository.syncPrefs
    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode
    val dynamicColor: StateFlow<Boolean> = preferencesRepository.dynamicColor
    val isFirstLaunch: StateFlow<Boolean> = preferencesRepository.isFirstLaunch
    val unreadCount: StateFlow<Int> = repository.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val monitoredCount: StateFlow<Int> = repository.monitoredCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val lastSyncTimestamp: StateFlow<Long> = preferencesRepository.lastSyncTimestamp
    val rateLimitInfo: StateFlow<Pair<Int, Int>> = preferencesRepository.rateLimitInfo
    val recentSyncLogs = repository.recentSyncLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notifications Filter State
    private val _selectedCategory = MutableStateFlow("ALL")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _notificationSearchQuery = MutableStateFlow("")
    val notificationSearchQuery: StateFlow<String> = _notificationSearchQuery.asStateFlow()

    private val _unreadOnlyFilter = MutableStateFlow(false)
    val unreadOnlyFilter: StateFlow<Boolean> = _unreadOnlyFilter.asStateFlow()

    val filteredNotifications: StateFlow<List<GitHubNotificationEntity>> =
        combine(
            repository.allNotifications,
            _selectedCategory,
            _unreadOnlyFilter,
            _notificationSearchQuery
        ) { notifs, category, unreadOnly, query ->
            var result = notifs
            if (category != "ALL") {
                result = result.filter { it.category.equals(category, ignoreCase = true) }
            }
            if (unreadOnly) {
                result = result.filter { !it.isRead }
            }
            if (query.isNotBlank()) {
                val q = query.trim()
                result = result.filter {
                    it.title.contains(q, ignoreCase = true) ||
                            it.repoFullName.contains(q, ignoreCase = true) ||
                            it.author.contains(q, ignoreCase = true) ||
                            it.body.contains(q, ignoreCase = true)
                }
            }
            result
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Latest activity regardless of the Notifications screen filters (used by the dashboard). */
    val recentNotifications: StateFlow<List<GitHubNotificationEntity>> =
        repository.allNotifications
            .map { it.take(RECENT_ACTIVITY_LIMIT) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Repositories Search & Filter State
    private val _repoSearchQuery = MutableStateFlow("")
    val repoSearchQuery: StateFlow<String> = _repoSearchQuery.asStateFlow()

    private val _isRefreshingRepos = MutableStateFlow(false)
    val isRefreshingRepos: StateFlow<Boolean> = _isRefreshingRepos.asStateFlow()

    /** One-shot message for a snackbar / toast; call [consumeUserMessage] once shown. */
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    /** Destination requested from outside the UI (e.g. a system notification's "View in App"). */
    private val _pendingNavigation = MutableStateFlow<NavDestination?>(null)
    val pendingNavigation: StateFlow<NavDestination?> = _pendingNavigation.asStateFlow()

    val filteredRepos: StateFlow<List<MonitoredRepoEntity>> =
        combine(repository.allRepos, _repoSearchQuery) { repos, query ->
            if (query.isBlank()) {
                repos
            } else {
                repos.filter {
                    it.fullName.contains(query, ignoreCase = true) ||
                            (it.description?.contains(query, ignoreCase = true) == true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Assistant Summary
    private val _assistantSummary = MutableStateFlow(AssistantSummary())
    val assistantSummary: StateFlow<AssistantSummary> = _assistantSummary.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allNotifications.collect {
                _assistantSummary.value = repository.getAssistantSummary()
            }
        }
        // Opening the app with stale data refreshes it immediately (periodic work may be far off).
        val dataAge = System.currentTimeMillis() - preferencesRepository.lastSyncTimestamp.value
        if (authState.value is AuthState.Authenticated && dataAge > STALE_DATA_THRESHOLD_MS) {
            triggerSync()
        }
    }

    fun setNotificationCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setNotificationSearchQuery(query: String) {
        _notificationSearchQuery.value = query
    }

    fun toggleUnreadOnlyFilter() {
        _unreadOnlyFilter.value = !_unreadOnlyFilter.value
    }

    fun setUnreadOnlyFilter(enabled: Boolean) {
        _unreadOnlyFilter.value = enabled
    }

    fun setRepoSearchQuery(query: String) {
        _repoSearchQuery.value = query
    }

    fun connectWithToken(token: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.connectWithToken(token)
            if (result.isSuccess) {
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.message ?: "Failed to connect")
            }
        }
    }

    fun generateOAuthState(): String = tokenManager.generateOAuthState()

    fun verifyOAuthState(state: String?): Boolean = tokenManager.verifyOAuthState(state)

    fun handleOAuthCode(code: String, state: String? = null, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            // The state parameter is mandatory: a callback without it cannot be tied to a request we made.
            if (!tokenManager.verifyOAuthState(state)) {
                val errorMsg = "OAuth state mismatch: Possible cross-site request forgery detected."
                tokenManager.setAuthError(errorMsg)
                onComplete(false, errorMsg)
                return@launch
            }

            val result = repository.exchangeOAuthToken(code)
            if (result.isSuccess) {
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.message)
            }
        }
    }


    fun reportOAuthError(message: String) {
        tokenManager.setAuthError(message)
    }

    fun saveOAuthConfiguration(clientId: String, clientSecret: String, redirectUri: String) {
        tokenManager.saveOAuthConfiguration(clientId, clientSecret, redirectUri)
    }

    fun getOAuthClientId(): String = tokenManager.getOAuthClientId()
    fun getOAuthClientSecret(): String = tokenManager.getOAuthClientSecret()
    fun getRedirectUri(): String = tokenManager.getRedirectUri()

    fun disconnect() {
        // Background work is cancelled by GitHubNotifierApp, which observes the session.
        viewModelScope.launch {
            repository.disconnect()
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            repository.syncAll()
            _assistantSummary.value = repository.getAssistantSummary()
        }
    }

    fun refreshRepositories() {
        if (_isRefreshingRepos.value) return
        viewModelScope.launch {
            _isRefreshingRepos.value = true
            val result = repository.refreshRepositories()
            _isRefreshingRepos.value = false
            result.exceptionOrNull()?.let { error ->
                _userMessage.value = when (error) {
                    is java.io.IOException -> "Offline: couldn't refresh repositories"
                    else -> "Couldn't refresh repositories: ${error.message ?: "unknown error"}"
                }
            }
        }
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    /** Handles "View in App" from a system notification: mark it read and show the inbox. */
    fun openNotificationFromSystem(notificationId: String) {
        markNotificationRead(notificationId)
        _selectedCategory.value = "ALL"
        _pendingNavigation.value = NavDestination.NOTIFICATIONS
    }

    fun consumePendingNavigation() {
        _pendingNavigation.value = null
    }

    fun toggleRepoMonitored(repoId: Long, isMonitored: Boolean) {
        viewModelScope.launch {
            repository.setRepoMonitored(repoId, isMonitored)
        }
    }

    fun toggleAllReposMonitored(isMonitored: Boolean) {
        viewModelScope.launch {
            repository.setAllReposMonitored(isMonitored)
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
            _assistantSummary.value = repository.getAssistantSummary()
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
            _assistantSummary.value = repository.getAssistantSummary()
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch {
            repository.deleteNotification(id)
            _assistantSummary.value = repository.getAssistantSummary()
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
            _assistantSummary.value = repository.getAssistantSummary()
        }
    }

    fun updateNotificationPreferences(prefs: NotificationPreferences) {
        preferencesRepository.updateNotificationPreferences(prefs)
    }

    fun updateSyncPreferences(prefs: SyncPreferences, context: Context) {
        preferencesRepository.updateSyncPreferences(prefs)
        WorkManagerScheduler.schedulePeriodicSync(
            context = context,
            intervalMinutes = prefs.intervalMinutes,
            wifiOnly = prefs.wifiOnly
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        preferencesRepository.setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        preferencesRepository.setDynamicColor(enabled)
    }

    fun completeFirstLaunch() {
        preferencesRepository.setFirstLaunchCompleted()
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            repository.createTestNotification()
            _assistantSummary.value = repository.getAssistantSummary()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            _assistantSummary.value = repository.getAssistantSummary()
        }
    }

    companion object {
        private const val RECENT_ACTIVITY_LIMIT = 10
        private const val STALE_DATA_THRESHOLD_MS = 5 * 60 * 1000L
        fun provideFactory(
            repository: GitHubRepository,
            tokenManager: TokenManager,
            preferencesRepository: PreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository, tokenManager, preferencesRepository) as T
            }
        }
    }
}

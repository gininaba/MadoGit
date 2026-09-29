package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.ApiClient
import com.example.data.auth.AuthState
import com.example.data.auth.TokenManager
import com.example.data.database.entities.GitHubNotificationEntity
import com.example.data.database.entities.MonitoredRepoEntity
import com.example.data.repository.AssistantSummary
import com.example.data.repository.GitHubRepository
import com.example.data.repository.NotificationPreferences
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SyncPreferences
import com.example.data.repository.SyncStatus
import com.example.data.repository.ThemeMode
import com.example.worker.WorkManagerScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    val filteredNotifications: StateFlow<List<GitHubNotificationEntity>> =
        combine(repository.allNotifications, _selectedCategory) { notifs, category ->
            if (category == "ALL") {
                notifs
            } else {
                notifs.filter { it.category.equals(category, ignoreCase = true) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Repositories Search & Filter State
    private val _repoSearchQuery = MutableStateFlow("")
    val repoSearchQuery: StateFlow<String> = _repoSearchQuery.asStateFlow()

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
    }

    fun setNotificationCategory(category: String) {
        _selectedCategory.value = category
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
            if (state != null && !tokenManager.verifyOAuthState(state)) {
                val errorMsg = "OAuth state mismatch: Possible cross-site request forgery detected."
                tokenManager.setAuthError(errorMsg)
                onComplete(false, errorMsg)
                return@launch
            }

            tokenManager.setAuthLoading()
            try {
                val clientId = tokenManager.getOAuthClientId()
                val clientSecret = tokenManager.getOAuthClientSecret()
                val redirectUri = tokenManager.getRedirectUri()

                val api = ApiClient.createRetrofit(tokenManager)
                val response = api.exchangeOAuthToken(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    code = code,
                    redirectUri = redirectUri
                )

                if (!response.accessToken.isNullOrBlank()) {
                    val connectResult = repository.connectWithToken(response.accessToken)
                    if (connectResult.isSuccess) {
                        onComplete(true, null)
                    } else {
                        onComplete(false, connectResult.exceptionOrNull()?.message)
                    }
                } else {
                    val errorMsg = response.errorDescription ?: response.error ?: "OAuth token exchange failed"
                    tokenManager.setAuthError(errorMsg)
                    onComplete(false, errorMsg)
                }
            } catch (e: Exception) {
                tokenManager.setAuthError(e.message ?: "OAuth Error")
                onComplete(false, e.message)
            }
        }
    }


    fun saveOAuthConfiguration(clientId: String, clientSecret: String, redirectUri: String) {
        tokenManager.saveOAuthConfiguration(clientId, clientSecret, redirectUri)
    }

    fun getOAuthClientId(): String = tokenManager.getOAuthClientId()
    fun getOAuthClientSecret(): String = tokenManager.getOAuthClientSecret()
    fun getRedirectUri(): String = tokenManager.getRedirectUri()

    fun disconnect(context: Context? = null) {
        viewModelScope.launch {
            repository.disconnect()
            context?.let { WorkManagerScheduler.cancelAll(it) }
        }
    }

    fun triggerSync(context: Context) {
        viewModelScope.launch {
            repository.syncAll(context)
            _assistantSummary.value = repository.getAssistantSummary()
        }
    }

    fun refreshRepositories() {
        viewModelScope.launch {
            repository.refreshRepositories()
        }
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

    fun sendTestNotification(context: Context) {
        viewModelScope.launch {
            repository.createTestNotification(context)
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

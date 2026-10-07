package com.aipos.madogit.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.aipos.madogit.data.api.ApiClient
import com.aipos.madogit.data.api.GitHubApiService
import com.aipos.madogit.data.api.models.GitHubNotificationDto
import com.aipos.madogit.data.api.models.GitHubRepoDto
import com.aipos.madogit.data.api.models.GitHubUserDto
import com.aipos.madogit.data.auth.AuthState
import com.aipos.madogit.data.auth.TokenManager
import com.aipos.madogit.data.database.AppDatabase
import com.aipos.madogit.data.database.entities.GitHubNotificationEntity
import com.aipos.madogit.data.database.entities.MonitoredRepoEntity
import com.aipos.madogit.data.database.entities.ProcessedEventEntity
import com.aipos.madogit.data.database.entities.SyncLogEntity
import com.aipos.madogit.notifications.NotificationDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class AssistantSummary(
    val pendingReviewRequests: Int = 0,
    val assignedIssues: Int = 0,
    val failedWorkflows: Int = 0,
    val unreadNotifications: Int = 0,
    val totalActionableItems: Int = 0,
    val topActionableItems: List<GitHubNotificationEntity> = emptyList()
)

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    /** [notice] carries a non-fatal advisory (e.g. reduced polling because of a low API quota). */
    data class Success(val newItemsCount: Int, val timestamp: Long, val notice: String? = null) : SyncStatus()
    data class Offline(val message: String) : SyncStatus()
    /** [retryable] is false for failures that will not fix themselves (e.g. revoked credentials). */
    data class Error(val message: String, val retryable: Boolean = true) : SyncStatus()
}

/** How aggressively a sync sweep may spend the GitHub REST quota. */
private enum class QuotaTier { NORMAL, CONSERVATIVE, CRITICAL }

class GitHubRepository(
    private val database: AppDatabase,
    private val tokenManager: TokenManager,
    private val preferencesRepository: PreferencesRepository,
    private val notificationDispatcher: NotificationDispatcher,
    apiServiceOverride: GitHubApiService? = null
) {
    private val repoDao = database.repoDao()
    private val notificationDao = database.notificationDao()
    private val processedEventDao = database.processedEventDao()
    private val syncLogDao = database.syncLogDao()

    // The auth interceptor reads the token from TokenManager on every request, so a single
    // client instance stays valid across sign-in / sign-out and never needs to be rebuilt.
    private val apiService: GitHubApiService = apiServiceOverride
        ?: ApiClient.createRetrofit(tokenManager) { remaining, limit ->
            preferencesRepository.updateRateLimit(remaining, limit)
        }

    /** Guarantees a single in-flight sync sweep (periodic worker, manual refresh, app start). */
    private val syncMutex = Mutex()

    /** Serializes repository list refreshes (sign-in, first sweep, pull-to-refresh). */
    private val repoRefreshMutex = Mutex()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    val allRepos: Flow<List<MonitoredRepoEntity>> = repoDao.getAllReposFlow()
    val monitoredRepos: Flow<List<MonitoredRepoEntity>> = repoDao.getMonitoredReposFlow()
    val allNotifications: Flow<List<GitHubNotificationEntity>> = notificationDao.getAllNotificationsFlow()
    val unreadCount: Flow<Int> = notificationDao.getUnreadCountFlow()
    val monitoredCount: Flow<Int> = repoDao.getMonitoredCountFlow()
    val recentSyncLogs: Flow<List<SyncLogEntity>> = syncLogDao.getRecentLogsFlow(10)

    fun getNotificationsByCategory(category: String): Flow<List<GitHubNotificationEntity>> {
        return if (category.uppercase() == "ALL") {
            notificationDao.getAllNotificationsFlow()
        } else {
            notificationDao.getNotificationsByCategoryFlow(category.uppercase())
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Authentication
    // ---------------------------------------------------------------------------------------------

    suspend fun connectWithToken(token: String): Result<GitHubUserDto> = withContext(Dispatchers.IO) {
        tokenManager.setAuthLoading()
        try {
            // Verify token directly with GitHub API before persisting
            val verifyService = ApiClient.createRetrofitWithToken(token)
            val user = verifyService.getCurrentUser()

            // Never show one account's cached (possibly private) data to another account.
            val previousAccount = preferencesRepository.cachedAccountLogin
            if (previousAccount != null && !previousAccount.equals(user.login, ignoreCase = true)) {
                wipeLocalAccountData()
            }

            tokenManager.saveAuthSuccess(token, user)
            preferencesRepository.setCachedAccountLogin(user.login)
            _isOffline.value = false

            // Fetch initial repositories
            refreshRepositories()

            Result.success(user)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            tokenManager.clearAuth()
            val message = when (e) {
                is HttpException -> {
                    if (e.code() == 401) "Invalid GitHub token. Please verify permissions."
                    else "GitHub API error: ${e.code()} ${e.message()}"
                }
                is IOException -> "Network error connecting to GitHub. Check internet connection."
                else -> e.message ?: "Authentication failed"
            }
            tokenManager.setAuthError(message)
            Result.failure(Exception(message))
        }
    }

    suspend fun exchangeOAuthToken(code: String): Result<GitHubUserDto> = withContext(Dispatchers.IO) {
        tokenManager.setAuthLoading()
        try {
            val clientId = tokenManager.getOAuthClientId()
            val clientSecret = tokenManager.getOAuthClientSecret()
            val redirectUri = tokenManager.getRedirectUri()

            val response = apiService.exchangeOAuthToken(
                clientId = clientId,
                clientSecret = clientSecret,
                code = code,
                redirectUri = redirectUri
            )

            if (!response.accessToken.isNullOrBlank()) {
                connectWithToken(response.accessToken)
            } else {
                val errorMsg = response.errorDescription ?: response.error ?: "OAuth token exchange failed"
                tokenManager.setAuthError(errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val message = if (e is IOException) {
                "Network error connecting to GitHub. Check internet connection."
            } else {
                e.message ?: "OAuth token exchange failed"
            }
            tokenManager.setAuthError(message)
            Result.failure(Exception(message))
        }
    }

    /** Signs out and removes every trace of the previous account from the device. */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        // Clearing the token first makes any in-flight sweep fail fast; the lock then ensures the
        // wipe is not interleaved with that sweep's database writes.
        tokenManager.clearAuth()
        syncMutex.withLock { wipeLocalAccountData() }
    }

    private fun wipeLocalAccountData() {
        database.clearAllTables()
        notificationDispatcher.cancelAll()
        preferencesRepository.resetSyncState()
        _syncStatus.value = SyncStatus.Idle
        _isOffline.value = false
    }

    /**
     * A 401 means the token was revoked or expired. Drop it so the app does not keep presenting a
     * signed-in UI (and retrying with dead credentials after every restart), then surface the reason.
     */
    private fun handleUnauthorized() {
        if (tokenManager.authState.value is AuthState.Authenticated) {
            tokenManager.clearAuth()
            tokenManager.setAuthError(SESSION_EXPIRED_MESSAGE)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Local state mutations
    // ---------------------------------------------------------------------------------------------

    suspend fun setRepoMonitored(repoId: Long, isMonitored: Boolean) = withContext(Dispatchers.IO) {
        repoDao.setMonitored(repoId, isMonitored)
    }

    suspend fun setAllReposMonitored(isMonitored: Boolean) = withContext(Dispatchers.IO) {
        repoDao.setAllMonitored(isMonitored)
    }

    suspend fun markNotificationAsRead(id: String) = withContext(Dispatchers.IO) {
        notificationDao.markAsRead(id)
        notificationDispatcher.cancel(id)
        try {
            // If it's a GitHub notification thread, inform GitHub API
            if (id.startsWith(THREAD_ID_PREFIX)) {
                val threadId = id.removePrefix(THREAD_ID_PREFIX)
                val response = apiService.markNotificationAsRead(threadId)
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to mark thread $threadId as read on GitHub: ${response.code()} ${response.message()}")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Exception marking notification as read on GitHub", e)
        }
    }

    suspend fun markAllNotificationsAsRead() = withContext(Dispatchers.IO) {
        notificationDao.markAllAsRead()
        notificationDispatcher.cancelAll()
        try {
            val response = apiService.markAllNotificationsAsRead()
            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to mark all notifications as read on GitHub: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Exception marking all notifications as read on GitHub", e)
        }
    }

    suspend fun deleteNotification(id: String) = withContext(Dispatchers.IO) {
        notificationDao.deleteNotification(id)
        notificationDispatcher.cancel(id)
    }

    suspend fun clearAllNotifications() = withContext(Dispatchers.IO) {
        notificationDao.clearAllNotifications()
        notificationDispatcher.cancelAll()
    }

    // ---------------------------------------------------------------------------------------------
    // Repository list
    // ---------------------------------------------------------------------------------------------

    /**
     * Reconciles the local repository table with every repository the user can access.
     *
     * - Paginates through `/user/repos` (up to [MAX_REPO_PAGES] x [REPO_PAGE_SIZE]).
     * - Preserves the user's explicit monitor / unmonitor choices and per-repo sync cursors.
     * - New repositories follow the "monitor all by default" preference.
     * - Repositories that are no longer accessible are removed.
     * - Only the very first import auto-monitors a small starter set.
     */
    suspend fun refreshRepositories(): Result<List<MonitoredRepoEntity>> = withContext(Dispatchers.IO) {
        repoRefreshMutex.withLock { refreshRepositoriesLocked() }
    }

    /** Refreshes the repository list only when it is empty or older than [REPO_REFRESH_INTERVAL_MS]. */
    private suspend fun refreshRepositoriesIfStale(): Result<List<MonitoredRepoEntity>>? =
        repoRefreshMutex.withLock {
            val isStale = System.currentTimeMillis() - preferencesRepository.lastRepoRefresh > REPO_REFRESH_INTERVAL_MS
            if (repoDao.getRepoCount() == 0 || isStale) refreshRepositoriesLocked() else null
        }

    private suspend fun refreshRepositoriesLocked(): Result<List<MonitoredRepoEntity>> {
        return try {
            val (remoteRepos, isComplete) = fetchAllUserRepos()
            _isOffline.value = false

            val monitorAll = preferencesRepository.syncPrefs.value.monitorAllByDefault
            val existing = repoDao.getAllReposSync().associateBy { it.id }
            val isFirstImport = existing.isEmpty()

            val repoEntities = remoteRepos.map { dto ->
                val previous = existing[dto.id]
                MonitoredRepoEntity(
                    id = dto.id,
                    fullName = dto.fullName,
                    name = dto.name,
                    owner = dto.owner.login,
                    isPrivate = dto.private,
                    description = dto.description,
                    stargazersCount = dto.stargazersCount,
                    forksCount = dto.forksCount,
                    defaultBranch = dto.defaultBranch,
                    htmlUrl = dto.htmlUrl,
                    isMonitored = previous?.isMonitored ?: monitorAll,
                    lastSyncedAt = previous?.lastSyncedAt ?: 0L,
                    language = dto.language
                )
            }

            val remoteIds = repoEntities.mapTo(HashSet()) { it.id }
            // Only prune when we have seen the complete list; a truncated listing must not delete data.
            val staleIds = if (isComplete) existing.keys.filterNot { it in remoteIds } else emptyList()

            database.withTransaction {
                repoDao.insertIgnore(repoEntities)
                repoDao.updateRepos(repoEntities)
                staleIds.chunked(SQL_IN_CHUNK).forEach { repoDao.deleteByIds(it) }

                // First import only: auto-monitor a starter set for a great onboarding experience.
                if (isFirstImport && !monitorAll) {
                    repoEntities.take(STARTER_REPO_COUNT).forEach { repoDao.setMonitored(it.id, true) }
                }
            }
            preferencesRepository.updateLastRepoRefresh()

            Result.success(repoDao.getAllReposSync())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error refreshing repositories", e)
            if (e.isUnauthorized()) handleUnauthorized()
            _isOffline.value = (e is IOException)
            Result.failure(e)
        }
    }

    /** @return the accessible repositories and whether the listing was exhaustive. */
    private suspend fun fetchAllUserRepos(): Pair<List<GitHubRepoDto>, Boolean> {
        val all = ArrayList<GitHubRepoDto>()
        for (page in 1..MAX_REPO_PAGES) {
            val batch = apiService.getUserRepos(perPage = REPO_PAGE_SIZE, sort = "updated", page = page)
            all += batch
            if (batch.size < REPO_PAGE_SIZE) return all.distinctBy { it.id } to true
        }
        return all.distinctBy { it.id } to false
    }

    // ---------------------------------------------------------------------------------------------
    // Sync engine
    // ---------------------------------------------------------------------------------------------

    /**
     * Runs one sync sweep. Concurrent callers do not queue up a second sweep: if one is already in
     * flight they return immediately and observe its outcome through [syncStatus].
     *
     * @return number of new items announced to the user.
     */
    suspend fun syncAll(): Int = (runSync() as? SyncStatus.Success)?.newItemsCount ?: 0

    /**
     * Runs one sync sweep and returns its own outcome ([SyncStatus.Syncing] if another sweep was
     * already in flight, [SyncStatus.Idle] when signed out).
     */
    suspend fun runSync(): SyncStatus = withContext(Dispatchers.IO) {
        if (tokenManager.authState.value !is AuthState.Authenticated) {
            _syncStatus.value = SyncStatus.Idle
            return@withContext SyncStatus.Idle
        }
        if (!syncMutex.tryLock()) return@withContext SyncStatus.Syncing
        try {
            performSync().also { _syncStatus.value = it }
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun performSync(): SyncStatus {
        _syncStatus.value = SyncStatus.Syncing
        var newNotificationsCount = 0

        try {
            // 1. Check Rate Limit (the /rate_limit endpoint does not consume quota)
            var remainingQuota = Int.MAX_VALUE
            try {
                val rateLimit = apiService.getRateLimit()
                remainingQuota = rateLimit.resources.core.remaining
                preferencesRepository.updateRateLimit(remainingQuota, rateLimit.resources.core.limit)
                _isOffline.value = false
            } catch (e: Exception) {
                if (e is CancellationException || e.isUnauthorized()) throw e
                if (e is IOException) {
                    _isOffline.value = true
                    return SyncStatus.Offline("Offline: showing saved data")
                }
                Log.w(TAG, "Rate limit check failed; continuing with default budget", e)
            }

            val tier = when {
                remainingQuota < RATE_CRITICAL_THRESHOLD -> QuotaTier.CRITICAL
                remainingQuota < RATE_CONSERVATIVE_THRESHOLD -> QuotaTier.CONSERVATIVE
                else -> QuotaTier.NORMAL
            }

            // Until one sweep has completed, everything we see is pre-existing history: import it
            // silently instead of flooding the shade with dozens of stale alerts.
            val isBaseline = syncLogDao.getSuccessfulSyncCount() == 0
            val notificationPreferences = preferencesRepository.notificationPrefs.value

            if (tier != QuotaTier.CRITICAL) {
                // 2. Fetch User Profile
                try {
                    tokenManager.updateCachedUser(apiService.getCurrentUser())
                } catch (e: Exception) {
                    if (e is CancellationException || e.isUnauthorized()) throw e
                    Log.w(TAG, "Profile refresh failed", e)
                }

                // 3. Keep the repository list fresh (cheap path: only when empty or stale)
                refreshRepositoriesIfStale()?.exceptionOrNull()?.let { if (it.isUnauthorized()) throw it }
            }

            purgeDuplicateNotifications()

            // 4. Check Official GitHub Notifications API (/notifications)
            newNotificationsCount += syncNotificationThreads(notificationPreferences, isBaseline)

            // 5. Monitor Specific Selected Repositories for Events & Workflows
            if (tier != QuotaTier.CRITICAL) {
                // Use round-robin ordering (least-recently synced first) to prevent starvation
                for (repo in repoDao.getMonitoredReposToSync(REPOS_PER_SWEEP)) {
                    newNotificationsCount += syncRepository(repo, notificationPreferences, tier, isBaseline)
                    // Update repository last synced timestamp
                    repoDao.updateLastSynced(repo.id, System.currentTimeMillis())
                }
            }

            val now = System.currentTimeMillis()
            preferencesRepository.updateLastSyncTime(now)
            val notice = when (tier) {
                QuotaTier.CRITICAL -> "API quota nearly exhausted ($remainingQuota left): only GitHub notifications were checked"
                QuotaTier.CONSERVATIVE -> "API quota low ($remainingQuota left): polling reduced to pull requests"
                QuotaTier.NORMAL -> null
            }
            writeSyncLog("SUCCESS", newNotificationsCount, null)
            return SyncStatus.Success(newNotificationsCount, now, notice)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Sync failed", e)
            val outcome: SyncStatus
            val logMessage = when {
                e.isUnauthorized() -> {
                    handleUnauthorized()
                    outcome = SyncStatus.Error(SESSION_EXPIRED_MESSAGE, retryable = false)
                    "Session expired or token revoked"
                }
                e is IOException -> {
                    _isOffline.value = true
                    outcome = SyncStatus.Offline("Offline: showing local data")
                    "Offline"
                }
                else -> {
                    val message = e.message ?: "Sync error"
                    outcome = SyncStatus.Error(message)
                    message
                }
            }
            writeSyncLog("FAILED", 0, logMessage)
            return outcome
        }
    }

    private suspend fun writeSyncLog(status: String, newItems: Int, error: String?) {
        syncLogDao.insertLog(
            SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                status = status,
                itemsFound = newItems,
                newNotificationsCount = newItems,
                errorMessage = error
            )
        )
        syncLogDao.pruneLogs(SYNC_LOG_RETENTION)
    }

    /**
     * Purge duplicate notifications in the local database (e.g. the same PR/issue captured both from
     * `/notifications` and from per-repo polling, or from historic ID format changes).
     *
     * Only rows pointing at a concrete PR / issue page are considered: fallback URLs (repo home,
     * actions page, releases list) are shared by many unrelated threads and must never be merged.
     */
    private suspend fun purgeDuplicateNotifications() {
        try {
            val keepers = mutableMapOf<String, GitHubNotificationEntity>()
            for (n in notificationDao.getAllNotificationsSync()) {
                if (!ITEM_URL_REGEX.matches(n.targetUrl)) continue
                val current = keepers[n.targetUrl]
                if (current == null) {
                    keepers[n.targetUrl] = n
                    continue
                }
                // Keep the most recent row; on ties prefer the GitHub thread (it syncs read state).
                val preferNew = n.timestamp > current.timestamp ||
                    (n.timestamp == current.timestamp && n.id.startsWith(THREAD_ID_PREFIX) && !current.id.startsWith(THREAD_ID_PREFIX))
                val (keep, drop) = if (preferNew) n to current else current to n
                // If either copy was read, the user has already seen it.
                if (drop.isRead && !keep.isRead) notificationDao.markAsRead(keep.id)
                notificationDao.deleteNotification(drop.id)
                keepers[n.targetUrl] = keep
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Duplicate purge failed", e)
        }
    }

    private suspend fun syncNotificationThreads(prefs: NotificationPreferences, isBaseline: Boolean): Int {
        val remoteNotifications = try {
            apiService.getNotifications(all = true, participating = false)
        } catch (e: Exception) {
            if (e is CancellationException || e.isUnauthorized()) throw e
            Log.w(TAG, "Error fetching /notifications", e)
            return 0
        }

        var announced = 0
        for (item in remoteNotifications) {
            val notifId = THREAD_ID_PREFIX + item.id
            val updatedAt = parseIsoDate(item.updatedAt)
            val existing = notificationDao.getNotificationById(notifId)

            if (existing != null) {
                when {
                    // New activity on a known thread (new comment, review, push...): resurface it.
                    item.unread && updatedAt > existing.timestamp -> {
                        val refreshed = mapThread(item).copy(isRead = false)
                        notificationDao.insert(refreshed)
                        if (!isBaseline) {
                            announced++
                            notificationDispatcher.post(refreshed, prefs)
                        }
                    }
                    // Read on GitHub (web / another device): mirror it locally.
                    // A local "read" is never undone by a stale unread flag from GitHub.
                    !item.unread && !existing.isRead -> {
                        notificationDao.markAsRead(notifId)
                        notificationDispatcher.cancel(notifId)
                    }
                }
                continue
            }

            // Threads the user deleted locally stay deleted.
            if (processedEventDao.isEventProcessed(notifId)) continue
            processedEventDao.insertProcessedEvent(
                ProcessedEventEntity(eventId = notifId, eventType = item.subject.type, repoFullName = item.repository.fullName)
            )

            val entity = mapThread(item)
            notificationDao.insert(entity)
            if (!entity.isRead && !isBaseline) {
                announced++
                notificationDispatcher.post(entity, prefs)
            }
        }
        return announced
    }

    private fun mapThread(item: GitHubNotificationDto): GitHubNotificationEntity {
        val reason = item.reason.lowercase()
        val category = when (item.subject.type.uppercase()) {
            "PULLREQUEST" -> "PR"
            "ISSUE" -> "ISSUE"
            "CHECKSUITE", "WORKFLOWRUN" -> "WORKFLOW"
            "RELEASE" -> "RELEASE"
            else -> "ACTIVITY"
        }
        val normalizedEventType = when (reason) {
            "assign" -> "ASSIGNED"
            "mention", "team_mention" -> "MENTION"
            "comment" -> "COMMENT"
            "review_requested" -> "REVIEW_REQUESTED"
            else -> item.reason.uppercase()
        }
        val actionState = when {
            reason.contains("review") -> "REVIEW_REQUESTED"
            reason == "assign" -> "ASSIGNED"
            else -> "UNREAD"
        }
        return GitHubNotificationEntity(
            id = THREAD_ID_PREFIX + item.id,
            eventType = normalizedEventType,
            category = category,
            repoFullName = item.repository.fullName,
            title = item.subject.title,
            body = "Reason: ${item.reason.replace('_', ' ')} (${item.subject.type})",
            author = item.repository.owner.login,
            avatarUrl = item.repository.owner.avatarUrl,
            targetUrl = convertApiUrlToHtmlUrl(item.subject.url)
                ?: item.repository.htmlUrl
                ?: "https://github.com/${item.repository.fullName}",
            timestamp = parseIsoDate(item.updatedAt),
            isRead = !item.unread,
            isNotified = true,
            actionState = actionState
        )
    }

    /** Polls one monitored repository, honouring notification preferences and the quota tier. */
    private suspend fun syncRepository(
        repo: MonitoredRepoEntity,
        prefs: NotificationPreferences,
        tier: QuotaTier,
        isBaseline: Boolean
    ): Int {
        val parts = repo.fullName.split("/")
        if (parts.size != 2) return 0
        val (owner, repoName) = parts
        // On first sync of a repo, past items are imported as already read.
        val markRead = repo.lastSyncedAt == 0L
        val announce = !isBaseline
        val fullSweep = tier == QuotaTier.NORMAL
        var announced = 0

        suspend fun guarded(label: String, block: suspend () -> Unit) {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException || e.isUnauthorized()) throw e
                Log.d(TAG, "$label not available or permission denied for ${repo.fullName}: ${e.message}")
            }
        }

        // Fetch workflow runs (CI/CD)
        if (fullSweep && prefs.actionMaster) guarded("Workflow runs") {
            for (run in apiService.getWorkflowRuns(owner, repoName, perPage = 5).workflowRuns) {
                val conclusion = run.conclusion?.uppercase() ?: run.status?.uppercase() ?: "PENDING"
                val isFailed = conclusion in FAILED_CONCLUSIONS
                val isSuccess = conclusion == "SUCCESS"
                val isCancelled = conclusion == "CANCELLED"

                // Only process if preferences allow it (actionSucceeded / actionCancelled are off by default)
                val shouldProcess = (isFailed && prefs.actionFailed) ||
                    (isSuccess && prefs.actionSucceeded) ||
                    (isCancelled && prefs.actionCancelled)
                if (!shouldProcess) continue

                val title = "${run.name ?: "Build"} #${run.runNumber}: $conclusion"
                val entity = GitHubNotificationEntity(
                    id = "gh_run_${run.id}",
                    eventType = when {
                        isFailed -> "WORKFLOW_FAILED"
                        isCancelled -> "WORKFLOW_CANCELLED"
                        else -> "WORKFLOW_SUCCESS"
                    },
                    category = "WORKFLOW",
                    repoFullName = repo.fullName,
                    title = title,
                    body = "Branch: ${run.headBranch ?: repo.defaultBranch} • Event: ${run.event ?: "push"}",
                    author = owner,
                    avatarUrl = null,
                    targetUrl = run.htmlUrl,
                    timestamp = parseIsoDate(run.updatedAt),
                    isNotified = true,
                    actionState = conclusion
                )
                if (storeRepoEvent(entity, "WORKFLOW_RUN", title, markRead, announce, prefs)) announced++
            }
        }

        val currentUsername = (tokenManager.authState.value as? AuthState.Authenticated)?.username

        // Fetch recently updated pull requests of any state (kept even in conservative mode:
        // review requests are high-value). Including closed PRs lets merges/closures be announced.
        if (prefs.prMaster) guarded("Pull requests") {
            for (pr in apiService.getPullRequests(owner, repoName, state = "all", perPage = 5)) {
                val isReviewRequested = !currentUsername.isNullOrBlank() &&
                    pr.requestedReviewers?.any { it.login.equals(currentUsername, ignoreCase = true) } == true
                val terminalState = when {
                    pr.mergedAt != null -> "MERGED"
                    pr.state.equals("closed", ignoreCase = true) -> "CLOSED"
                    else -> null
                }
                val title = "PR #${pr.number}: ${pr.title}"
                val entity = GitHubNotificationEntity(
                    id = "gh_pr_${pr.id}",
                    eventType = terminalState ?: if (isReviewRequested) "REVIEW_REQUESTED" else "PR_OPENED",
                    category = "PR",
                    repoFullName = repo.fullName,
                    title = title,
                    body = when {
                        terminalState == "MERGED" -> "Merged • opened by @${pr.user.login}"
                        terminalState == "CLOSED" -> "Closed without merging • opened by @${pr.user.login}"
                        isReviewRequested -> "Review requested from you by @${pr.user.login}"
                        else -> "Opened by @${pr.user.login} • ${if (pr.draft) "Draft" else "Ready for review"}"
                    },
                    author = pr.user.login,
                    avatarUrl = pr.user.avatarUrl,
                    targetUrl = pr.htmlUrl,
                    timestamp = parseIsoDate(pr.updatedAt),
                    isNotified = true,
                    actionState = terminalState ?: if (isReviewRequested) "REVIEW_REQUESTED" else "OPEN"
                )
                val announcedNow = if (terminalState != null) {
                    storePullRequestTransition(entity, title, markRead, announce, prefs)
                } else {
                    storeRepoEvent(entity, "PULL_REQUEST", title, markRead, announce, prefs)
                }
                if (announcedNow) announced++
            }
        }

        // Fetch issues
        if (fullSweep && prefs.issueMaster) guarded("Issues") {
            for (issue in apiService.getIssues(owner, repoName, state = "open", perPage = 5)) {
                if (issue.pullRequest != null) continue
                val isAssigned = !currentUsername.isNullOrBlank() &&
                    issue.assignees?.any { it.login.equals(currentUsername, ignoreCase = true) } == true
                val title = "Issue #${issue.number}: ${issue.title}"
                val entity = GitHubNotificationEntity(
                    id = "gh_issue_${issue.id}",
                    eventType = if (isAssigned) "ASSIGNED" else "ISSUE_OPENED",
                    category = "ISSUE",
                    repoFullName = repo.fullName,
                    title = title,
                    body = if (isAssigned) "Assigned to you by @${issue.user.login}"
                           else "Opened by @${issue.user.login} in ${repo.name}",
                    author = issue.user.login,
                    avatarUrl = issue.user.avatarUrl,
                    targetUrl = issue.htmlUrl,
                    timestamp = parseIsoDate(issue.updatedAt),
                    isNotified = true,
                    actionState = if (isAssigned) "ASSIGNED" else "OPEN"
                )
                if (storeRepoEvent(entity, "ISSUE", title, markRead, announce, prefs)) announced++
            }
        }

        // Fetch releases
        if (fullSweep && prefs.releaseMaster) guarded("Releases") {
            for (rel in apiService.getReleases(owner, repoName, perPage = 3)) {
                val entity = GitHubNotificationEntity(
                    id = "gh_rel_${rel.id}",
                    eventType = "RELEASE_PUBLISHED",
                    category = "RELEASE",
                    repoFullName = repo.fullName,
                    title = "Release ${rel.name ?: rel.tagName}",
                    body = "Tag: ${rel.tagName}${if (rel.prerelease) " (Pre-release)" else ""}",
                    author = rel.author?.login ?: owner,
                    avatarUrl = rel.author?.avatarUrl,
                    targetUrl = rel.htmlUrl,
                    timestamp = parseIsoDate(rel.publishedAt),
                    isNotified = true,
                    actionState = "PUBLISHED"
                )
                if (storeRepoEvent(entity, "RELEASE", null, markRead, announce, prefs)) announced++
            }
        }

        return announced
    }

    /**
     * Stores a polled repository event once. Skips anything already known by id, target URL or
     * (repo, title), and anything previously processed (including items the user deleted).
     *
     * @return true when the event was announced to the user.
     */
    private suspend fun storeRepoEvent(
        entity: GitHubNotificationEntity,
        processedType: String,
        dedupeTitle: String?,
        markRead: Boolean,
        announce: Boolean,
        prefs: NotificationPreferences
    ): Boolean {
        val existing = notificationDao.getNotificationById(entity.id)
            ?: entity.targetUrl.takeIf { it.isNotBlank() }?.let { notificationDao.getNotificationByTargetUrl(it) }
            ?: dedupeTitle?.let { notificationDao.getNotificationByRepoAndTitle(entity.repoFullName, it) }
        if (existing != null || processedEventDao.isEventProcessed(entity.id)) return false

        processedEventDao.insertProcessedEvent(
            ProcessedEventEntity(eventId = entity.id, eventType = processedType, repoFullName = entity.repoFullName)
        )
        val stored = entity.copy(isRead = markRead)
        notificationDao.insert(stored)
        if (stored.isRead || !announce) return false
        notificationDispatcher.post(stored, prefs)
        return true
    }

    /**
     * Records a merged/closed pull request. A PR already stored in another state is updated in place
     * and resurfaced as unread; unseen PRs go through [storeRepoEvent] (so user-deleted items stay deleted).
     *
     * @return true when the transition was announced to the user.
     */
    private suspend fun storePullRequestTransition(
        entity: GitHubNotificationEntity,
        dedupeTitle: String,
        markRead: Boolean,
        announce: Boolean,
        prefs: NotificationPreferences
    ): Boolean {
        val existing = notificationDao.getNotificationById(entity.id)
            ?: return storeRepoEvent(entity, "PULL_REQUEST", dedupeTitle, markRead, announce, prefs)
        if (existing.actionState == entity.actionState) return false

        val updated = entity.copy(isRead = markRead)
        notificationDao.insert(updated)
        if (updated.isRead || !announce) return false
        notificationDispatcher.post(updated, prefs)
        return true
    }

    // ---------------------------------------------------------------------------------------------
    // Assistant / maintenance
    // ---------------------------------------------------------------------------------------------

    suspend fun getAssistantSummary(): AssistantSummary = withContext(Dispatchers.IO) {
        val unread = notificationDao.getUnreadNotifications()

        var pendingReviews = 0
        var assignedIssues = 0
        var failedWorkflows = 0
        val actionableItems = mutableListOf<GitHubNotificationEntity>()

        for (item in unread) {
            when {
                item.category == "WORKFLOW" && isFailedWorkflow(item) -> {
                    failedWorkflows++
                    actionableItems.add(item)
                }
                item.category == "PR" && (item.actionState == "REVIEW_REQUESTED" || item.eventType.contains("REVIEW")) -> {
                    pendingReviews++
                    actionableItems.add(item)
                }
                item.category == "ISSUE" && (item.eventType == "ASSIGNED" || item.actionState == "ASSIGNED") -> {
                    assignedIssues++
                    actionableItems.add(item)
                }
                else -> {
                    // Other unread notifications
                }
            }
        }

        AssistantSummary(
            pendingReviewRequests = pendingReviews,
            assignedIssues = assignedIssues,
            failedWorkflows = failedWorkflows,
            unreadNotifications = unread.size,
            totalActionableItems = pendingReviews + assignedIssues + failedWorkflows,
            topActionableItems = actionableItems
        )
    }

    private fun isFailedWorkflow(item: GitHubNotificationEntity): Boolean =
        item.eventType == "WORKFLOW_FAILED" ||
            item.actionState?.uppercase()?.let { it in FAILED_CONCLUSIONS } == true ||
            // GitHub CheckSuite threads: "CI workflow run failed for main branch"
            (item.id.startsWith(THREAD_ID_PREFIX) && item.title.contains("failed", ignoreCase = true))

    /**
     * Clears cached notifications and sync history. The next sweep is treated as a fresh baseline,
     * so re-imported history is stored silently rather than re-announced.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        notificationDao.clearAllNotifications()
        processedEventDao.clearProcessedEvents()
        syncLogDao.clearLogs()
        notificationDispatcher.cancelAll()
    }

    suspend fun createTestNotification() = withContext(Dispatchers.IO) {
        val testId = "test_event_${System.currentTimeMillis()}"
        val testNotif = GitHubNotificationEntity(
            id = testId,
            eventType = "REVIEW_REQUESTED",
            category = "PR",
            repoFullName = "github/hub",
            title = "PR #429: Add notification background dispatcher",
            body = "octocat requested your review on main branch",
            author = "octocat",
            avatarUrl = "https://github.com/octocat.png",
            targetUrl = "https://github.com/github/hub/pull/429",
            timestamp = System.currentTimeMillis(),
            isRead = false,
            isNotified = true,
            actionState = "REVIEW_REQUESTED"
        )
        notificationDao.insert(testNotif)
        notificationDispatcher.post(testNotif, preferencesRepository.notificationPrefs.value)
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Maps a REST subject URL (`https://api.github.com/repos/{o}/{r}/{kind}/{id}`) to the matching
     * web page. Returns null for kinds without a stable web equivalent so callers fall back to the
     * repository page instead of producing a 404 link.
     */
    private fun convertApiUrlToHtmlUrl(apiUrl: String?): String? {
        val match = apiUrl?.let { API_SUBJECT_URL_REGEX.find(it) } ?: return null
        val (owner, repo, kind, id) = match.destructured
        val base = "https://github.com/$owner/$repo"
        return when (kind) {
            "pulls" -> if (id.isNotEmpty()) "$base/pull/$id" else null
            "issues" -> if (id.isNotEmpty()) "$base/issues/$id" else null
            "commits" -> if (id.isNotEmpty()) "$base/commit/$id" else null
            "discussions" -> if (id.isNotEmpty()) "$base/discussions/$id" else null
            // The API exposes numeric release ids, which are not valid in web URLs.
            "releases" -> "$base/releases"
            else -> null
        }
    }

    private fun Throwable.isUnauthorized(): Boolean = this is HttpException && code() == 401

    fun parseIsoDate(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                return java.time.Instant.parse(iso).toEpochMilli()
            } catch (_: Exception) {}
            try {
                return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
            } catch (_: Exception) {}
        }
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = format.parse(iso)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    companion object {
        private const val TAG = "GitHubRepository"
        private const val THREAD_ID_PREFIX = "gh_thread_"
        private const val SESSION_EXPIRED_MESSAGE = "Session expired or token revoked. Please sign in again."

        /** Below this many remaining core requests only `/notifications` is polled. */
        const val RATE_CRITICAL_THRESHOLD = 100
        /** Below this many remaining core requests only pull requests are polled per repo. */
        const val RATE_CONSERVATIVE_THRESHOLD = 500

        private const val REPO_PAGE_SIZE = 100
        private const val MAX_REPO_PAGES = 10
        private const val REPO_REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1000L
        private const val STARTER_REPO_COUNT = 3
        private const val REPOS_PER_SWEEP = 5
        private const val SYNC_LOG_RETENTION = 200
        private const val SQL_IN_CHUNK = 500

        private val FAILED_CONCLUSIONS = setOf("FAILURE", "FAILED", "TIMED_OUT", "STARTUP_FAILURE")
        private val ITEM_URL_REGEX = Regex("^https://github\\.com/[^/]+/[^/]+/(pull|issues)/\\d+$")
        private val API_SUBJECT_URL_REGEX =
            Regex("^https://api\\.github\\.com/repos/([^/]+)/([^/]+)/([^/?#]+)(?:/([^/?#]+))?")
    }
}

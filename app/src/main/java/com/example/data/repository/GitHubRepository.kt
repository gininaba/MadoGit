package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.api.ApiClient
import com.example.data.api.GitHubApiService
import com.example.data.api.models.GitHubNotificationDto
import com.example.data.api.models.GitHubRepoDto
import com.example.data.api.models.GitHubUserDto
import com.example.data.auth.AuthState
import com.example.data.auth.TokenManager
import com.example.data.database.AppDatabase
import com.example.data.database.entities.GitHubNotificationEntity
import com.example.data.database.entities.MonitoredRepoEntity
import com.example.data.database.entities.ProcessedEventEntity
import com.example.data.database.entities.SyncLogEntity
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    data class Success(val newItemsCount: Int, val timestamp: Long) : SyncStatus()
    data class Offline(val message: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

class GitHubRepository(
    private val database: AppDatabase,
    private val tokenManager: TokenManager,
    private val preferencesRepository: PreferencesRepository
) {
    private val repoDao = database.repoDao()
    private val notificationDao = database.notificationDao()
    private val processedEventDao = database.processedEventDao()
    private val syncLogDao = database.syncLogDao()

    private var apiService: GitHubApiService = ApiClient.createRetrofit(tokenManager)

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

    suspend fun connectWithToken(token: String): Result<GitHubUserDto> = withContext(Dispatchers.IO) {
        tokenManager.setAuthLoading()
        try {
            // Verify token directly with GitHub API before persisting
            val verifyService = ApiClient.createRetrofitWithToken(token)
            val user = verifyService.getCurrentUser()

            tokenManager.saveAuthSuccess(token, user)
            apiService = ApiClient.createRetrofit(tokenManager)
            _isOffline.value = false

            // Fetch initial repositories
            refreshRepositories()

            Result.success(user)
        } catch (e: Exception) {
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

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        tokenManager.clearAuth()
    }

    suspend fun setRepoMonitored(repoId: Long, isMonitored: Boolean) = withContext(Dispatchers.IO) {
        repoDao.setMonitored(repoId, isMonitored)
    }

    suspend fun setAllReposMonitored(isMonitored: Boolean) = withContext(Dispatchers.IO) {
        repoDao.setAllMonitored(isMonitored)
    }

    suspend fun markNotificationAsRead(id: String) = withContext(Dispatchers.IO) {
        notificationDao.markAsRead(id)
        try {
            // If it's a GitHub notification thread, inform GitHub API
            if (id.startsWith("gh_thread_")) {
                val threadId = id.removePrefix("gh_thread_")
                apiService.markNotificationAsRead(threadId)
            }
        } catch (_: Exception) {
            // Non-critical background failure
        }
    }

    suspend fun markAllNotificationsAsRead() = withContext(Dispatchers.IO) {
        notificationDao.markAllAsRead()
        try {
            apiService.markAllNotificationsAsRead()
        } catch (_: Exception) {
            // Non-critical background failure (e.g. offline or token scope restriction)
        }
    }

    suspend fun deleteNotification(id: String) = withContext(Dispatchers.IO) {
        notificationDao.deleteNotification(id)
    }

    suspend fun clearAllNotifications() = withContext(Dispatchers.IO) {
        notificationDao.clearAllNotifications()
    }

    suspend fun refreshRepositories(): Result<List<MonitoredRepoEntity>> = withContext(Dispatchers.IO) {
        try {
            val remoteRepos = apiService.getUserRepos(perPage = 100, sort = "updated")
            _isOffline.value = false

            val currentMonitored = repoDao.getMonitoredReposSync().associateBy { it.id }
            val monitorAll = preferencesRepository.syncPrefs.value.monitorAllByDefault

            val repoEntities = remoteRepos.map { dto ->
                val wasMonitored = currentMonitored[dto.id]?.isMonitored ?: monitorAll
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
                    isMonitored = wasMonitored,
                    lastSyncedAt = System.currentTimeMillis()
                )
            }

            // Save to database
            repoDao.insertIgnore(repoEntities)
            repoDao.updateRepos(repoEntities)

            // If no repos are monitored and user has repos, auto-monitor first 3 for great onboarding
            val monitoredNow = repoDao.getMonitoredReposSync()
            if (monitoredNow.isEmpty() && repoEntities.isNotEmpty()) {
                val initialMonitored = repoEntities.take(3)
                initialMonitored.forEach { repoDao.setMonitored(it.id, true) }
            }

            Result.success(repoEntities)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("GitHubRepository", "Error refreshing repositories", e)
            if (e is HttpException && e.code() == 401) {
                tokenManager.setAuthError("Session expired or token revoked. Please sign in again.")
            }
            _isOffline.value = (e is IOException)
            Result.failure(e)
        }
    }

    suspend fun syncAll(context: Context): Int = withContext(Dispatchers.IO) {
        val auth = tokenManager.authState.value
        if (auth !is AuthState.Authenticated) {
            _syncStatus.value = SyncStatus.Idle
            return@withContext 0
        }

        _syncStatus.value = SyncStatus.Syncing
        val startTime = System.currentTimeMillis()
        var newNotificationsCount = 0

        try {
            // 1. Check Rate Limit
            try {
                val rateLimit = apiService.getRateLimit()
                preferencesRepository.updateRateLimit(
                    rateLimit.resources.core.remaining,
                    rateLimit.resources.core.limit
                )
                _isOffline.value = false
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 401) {
                    throw e
                }
                if (e is IOException) {
                    _isOffline.value = true
                    _syncStatus.value = SyncStatus.Offline("Offline: showing saved data")
                    return@withContext 0
                }
            }

            // 2. Fetch User Profile
            try {
                val user = apiService.getCurrentUser()
                tokenManager.updateCachedUser(user)
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 401) throw e
            }

            // 3. Ensure repositories are up-to-date
            var monitoredReposList = repoDao.getMonitoredReposSync()
            if (monitoredReposList.isEmpty()) {
                refreshRepositories()
                monitoredReposList = repoDao.getMonitoredReposSync()
            }

            val notificationPreferences = preferencesRepository.notificationPrefs.value

            // Purge any duplicate notifications in the local database (e.g. from ID format changes)
            try {
                val allNotifs = notificationDao.getAllNotificationsSync()
                val seenKeys = mutableMapOf<String, GitHubNotificationEntity>()
                for (n in allNotifs) {
                    val key = if (n.targetUrl.isNotBlank()) n.targetUrl else "${n.repoFullName}::${n.title}"
                    val existingSeen = seenKeys[key]
                    if (existingSeen != null) {
                        // If one is read and the other is unread, keep the read one and delete the unread duplicate
                        if (existingSeen.isRead && !n.isRead) {
                            notificationDao.deleteNotification(n.id)
                        } else if (!existingSeen.isRead && n.isRead) {
                            notificationDao.deleteNotification(existingSeen.id)
                            seenKeys[key] = n
                        } else {
                            notificationDao.deleteNotification(n.id)
                        }
                    } else {
                        seenKeys[key] = n
                    }
                }
            } catch (_: Exception) {}

            // 4. Check Official GitHub Notifications API (/notifications)
            try {
                val remoteNotifications = apiService.getNotifications(all = true, participating = false)
                for (item in remoteNotifications) {
                    val notifId = "gh_thread_${item.id}"
                    val existing = notificationDao.getNotificationById(notifId)

                    if (existing != null) {
                        // Reconcile read status:
                        // If user marked read locally (existing.isRead), keep it read.
                        // If read on GitHub (!item.unread), update local state to read.
                        val shouldBeRead = existing.isRead || !item.unread
                        if (shouldBeRead != existing.isRead && shouldBeRead) {
                            notificationDao.markAsRead(notifId)
                        }
                        continue
                    }

                    if (!processedEventDao.isEventProcessed(notifId)) {
                        processedEventDao.insertProcessedEvent(
                            ProcessedEventEntity(
                                eventId = notifId,
                                eventType = item.subject.type,
                                repoFullName = item.repository.fullName
                            )
                        )

                        val category = when (item.subject.type.uppercase()) {
                            "PULLREQUEST" -> "PR"
                            "ISSUE" -> "ISSUE"
                            "CHECKSUITE", "WORKFLOWRUN" -> "WORKFLOW"
                            "RELEASE" -> "RELEASE"
                            else -> "ACTIVITY"
                        }

                        val normalizedEventType = when (item.reason.lowercase()) {
                            "assign" -> "ASSIGNED"
                            "mention", "team_mention" -> "MENTION"
                            "comment" -> "COMMENT"
                            "review_requested" -> "REVIEW_REQUESTED"
                            else -> item.reason.uppercase()
                        }

                        val notifActionState = when {
                            item.reason.lowercase().contains("review") -> "REVIEW_REQUESTED"
                            item.reason.lowercase() == "assign" -> "ASSIGNED"
                            else -> "UNREAD"
                        }

                        val isRead = !item.unread
                        val entity = GitHubNotificationEntity(
                            id = notifId,
                            eventType = normalizedEventType,
                            category = category,
                            repoFullName = item.repository.fullName,
                            title = item.subject.title,
                            body = "Reason: ${item.reason.replace('_', ' ')} (${item.subject.type})",
                            author = item.repository.owner.login,
                            avatarUrl = item.repository.owner.avatarUrl,
                            targetUrl = item.repository.htmlUrl ?: "https://github.com/${item.repository.fullName}",
                            timestamp = parseIsoDate(item.updatedAt),
                            isRead = isRead,
                            isNotified = true,
                            actionState = notifActionState
                        )

                        notificationDao.insert(entity)
                        if (!isRead) {
                            newNotificationsCount++
                            NotificationHelper.postNotification(context, entity, notificationPreferences)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 401) throw e
                Log.w("GitHubRepository", "Error fetching /notifications", e)
            }

            // 5. Monitor Specific Selected Repositories for Events & Workflows
            // Use round-robin ordering (least-recently synced first) to prevent starvation
            val reposToPoll = repoDao.getMonitoredReposToSync(5)
            for (repo in reposToPoll) {
                val parts = repo.fullName.split("/")
                if (parts.size != 2) continue
                val owner = parts[0]
                val repoName = parts[1]
                val isFirstSyncForRepo = repo.lastSyncedAt == 0L

                // Fetch workflow runs (CI/CD)
                if (notificationPreferences.actionMaster) {
                    try {
                        val runsResponse = apiService.getWorkflowRuns(owner, repoName, perPage = 5)
                        for (run in runsResponse.workflowRuns) {
                            val conclusion = run.conclusion?.uppercase() ?: run.status?.uppercase() ?: "PENDING"
                            val isFailed = conclusion in listOf("FAILURE", "FAILED", "TIMED_OUT")
                            val isSuccess = conclusion == "SUCCESS"

                            // Only process if preferences allow it (actionSucceeded is false by default)
                            val shouldProcess = (isFailed && notificationPreferences.actionFailed) ||
                                                (isSuccess && notificationPreferences.actionSucceeded)
                            if (!shouldProcess) {
                                continue
                            }

                            val runEventId = "gh_run_${run.id}"
                            val existing = notificationDao.getNotificationById(runEventId)
                                ?: (if (!run.htmlUrl.isNullOrBlank()) notificationDao.getNotificationByTargetUrl(run.htmlUrl) else null)
                                ?: notificationDao.getNotificationByRepoAndTitle(repo.fullName, "${run.name ?: "Build"} #${run.runNumber}: $conclusion")

                            if (existing != null) {
                                continue
                            }

                            if (!processedEventDao.isEventProcessed(runEventId)) {
                                processedEventDao.insertProcessedEvent(
                                    ProcessedEventEntity(
                                        eventId = runEventId,
                                        eventType = "WORKFLOW_RUN",
                                        repoFullName = repo.fullName
                                    )
                                )

                                // On first sync of a repo, past completed runs are marked as read
                                val isRead = isFirstSyncForRepo

                                val notif = GitHubNotificationEntity(
                                    id = runEventId,
                                    eventType = if (isFailed) "WORKFLOW_FAILED" else "WORKFLOW_SUCCESS",
                                    category = "WORKFLOW",
                                    repoFullName = repo.fullName,
                                    title = "${run.name ?: "Build"} #${run.runNumber}: $conclusion",
                                    body = "Branch: ${run.headBranch ?: repo.defaultBranch} • Event: ${run.event ?: "push"}",
                                    author = owner,
                                    avatarUrl = null,
                                    targetUrl = run.htmlUrl,
                                    timestamp = parseIsoDate(run.updatedAt),
                                    isRead = isRead,
                                    isNotified = true,
                                    actionState = conclusion
                                )

                                notificationDao.insert(notif)
                                if (!isRead) {
                                    newNotificationsCount++
                                    NotificationHelper.postNotification(context, notif, notificationPreferences)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("GitHubRepository", "Workflow runs not available or permission denied for $owner/$repoName")
                    }
                }

                // Fetch pull requests
                if (notificationPreferences.prMaster) {
                    try {
                        val prs = apiService.getPullRequests(owner, repoName, state = "open", perPage = 5)
                        for (pr in prs) {
                            val prEventId = "gh_pr_${pr.id}"
                            val existing = notificationDao.getNotificationById(prEventId)
                                ?: (if (!pr.htmlUrl.isNullOrBlank()) notificationDao.getNotificationByTargetUrl(pr.htmlUrl) else null)
                                ?: notificationDao.getNotificationByRepoAndTitle(repo.fullName, "PR #${pr.number}: ${pr.title}")

                            if (existing != null) {
                                continue
                            }

                            if (!processedEventDao.isEventProcessed(prEventId)) {
                                processedEventDao.insertProcessedEvent(
                                    ProcessedEventEntity(
                                        eventId = prEventId,
                                        eventType = "PULL_REQUEST",
                                        repoFullName = repo.fullName
                                    )
                                )

                                val currentUsername = (tokenManager.authState.value as? AuthState.Authenticated)?.username
                                val isReviewRequested = !currentUsername.isNullOrBlank() &&
                                        pr.requestedReviewers?.any { it.login.equals(currentUsername, ignoreCase = true) } == true

                                val prEventType = if (isReviewRequested) "REVIEW_REQUESTED" else "PR_OPENED"
                                val prActionState = if (isReviewRequested) "REVIEW_REQUESTED" else "OPEN"

                                // On first sync of a repo, mark as read unless review is explicitly requested from current user
                                val isRead = if (isFirstSyncForRepo) !isReviewRequested else false

                                val notif = GitHubNotificationEntity(
                                    id = prEventId,
                                    eventType = prEventType,
                                    category = "PR",
                                    repoFullName = repo.fullName,
                                    title = "PR #${pr.number}: ${pr.title}",
                                    body = if (isReviewRequested) "Review requested from you by @${pr.user.login}"
                                           else "Opened by @${pr.user.login} • ${if (pr.draft) "Draft" else "Ready for review"}",
                                    author = pr.user.login,
                                    avatarUrl = pr.user.avatarUrl,
                                    targetUrl = pr.htmlUrl,
                                    timestamp = parseIsoDate(pr.updatedAt),
                                    isRead = isRead,
                                    isNotified = true,
                                    actionState = prActionState
                                )

                                notificationDao.insert(notif)
                                if (!isRead) {
                                    newNotificationsCount++
                                    NotificationHelper.postNotification(context, notif, notificationPreferences)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Fetch issues
                if (notificationPreferences.issueMaster) {
                    try {
                        val issues = apiService.getIssues(owner, repoName, state = "open", perPage = 5)
                        for (issue in issues) {
                            if (issue.pullRequest != null) continue

                            val issueEventId = "gh_issue_${issue.id}"
                            val existing = notificationDao.getNotificationById(issueEventId)
                                ?: (if (!issue.htmlUrl.isNullOrBlank()) notificationDao.getNotificationByTargetUrl(issue.htmlUrl) else null)
                                ?: notificationDao.getNotificationByRepoAndTitle(repo.fullName, "Issue #${issue.number}: ${issue.title}")

                            if (existing != null) {
                                continue
                            }

                            if (!processedEventDao.isEventProcessed(issueEventId)) {
                                processedEventDao.insertProcessedEvent(
                                    ProcessedEventEntity(
                                        eventId = issueEventId,
                                        eventType = "ISSUE",
                                        repoFullName = repo.fullName
                                    )
                                )

                                val currentUsername = (tokenManager.authState.value as? AuthState.Authenticated)?.username
                                val isAssigned = !currentUsername.isNullOrBlank() &&
                                        issue.assignees?.any { it.login.equals(currentUsername, ignoreCase = true) } == true

                                val issueEventType = if (isAssigned) "ASSIGNED" else "ISSUE_OPENED"
                                val issueActionState = if (isAssigned) "ASSIGNED" else "OPEN"

                                // On first sync of a repo, mark as read unless assigned to current user
                                val isRead = if (isFirstSyncForRepo) !isAssigned else false

                                val notif = GitHubNotificationEntity(
                                    id = issueEventId,
                                    eventType = issueEventType,
                                    category = "ISSUE",
                                    repoFullName = repo.fullName,
                                    title = "Issue #${issue.number}: ${issue.title}",
                                    body = if (isAssigned) "Assigned to you by @${issue.user.login}"
                                           else "Opened by @${issue.user.login} in ${repo.name}",
                                    author = issue.user.login,
                                    avatarUrl = issue.user.avatarUrl,
                                    targetUrl = issue.htmlUrl,
                                    timestamp = parseIsoDate(issue.updatedAt),
                                    isRead = isRead,
                                    isNotified = true,
                                    actionState = issueActionState
                                )

                                notificationDao.insert(notif)
                                if (!isRead) {
                                    newNotificationsCount++
                                    NotificationHelper.postNotification(context, notif, notificationPreferences)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Fetch releases
                if (notificationPreferences.releaseMaster) {
                    try {
                        val releases = apiService.getReleases(owner, repoName, perPage = 3)
                        for (rel in releases) {
                            val relEventId = "gh_rel_${rel.id}"
                            val existing = notificationDao.getNotificationById(relEventId)
                                ?: (if (!rel.htmlUrl.isNullOrBlank()) notificationDao.getNotificationByTargetUrl(rel.htmlUrl) else null)

                            if (existing != null) {
                                continue
                            }

                            if (!processedEventDao.isEventProcessed(relEventId)) {
                                processedEventDao.insertProcessedEvent(
                                    ProcessedEventEntity(
                                        eventId = relEventId,
                                        eventType = "RELEASE",
                                        repoFullName = repo.fullName
                                    )
                                )

                                val isRead = isFirstSyncForRepo

                                val notif = GitHubNotificationEntity(
                                    id = relEventId,
                                    eventType = "RELEASE_PUBLISHED",
                                    category = "RELEASE",
                                    repoFullName = repo.fullName,
                                    title = "Release ${rel.name ?: rel.tagName}",
                                    body = "Tag: ${rel.tagName}${if (rel.prerelease) " (Pre-release)" else ""}",
                                    author = rel.author?.login ?: owner,
                                    avatarUrl = rel.author?.avatarUrl,
                                    targetUrl = rel.htmlUrl,
                                    timestamp = parseIsoDate(rel.publishedAt),
                                    isRead = isRead,
                                    isNotified = true,
                                    actionState = "PUBLISHED"
                                )

                                notificationDao.insert(notif)
                                if (!isRead) {
                                    newNotificationsCount++
                                    NotificationHelper.postNotification(context, notif, notificationPreferences)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Update repository last synced timestamp
                repoDao.updateLastSynced(repo.id, System.currentTimeMillis())
            }

            preferencesRepository.updateLastSyncTime(System.currentTimeMillis())
            _syncStatus.value = SyncStatus.Success(newNotificationsCount, System.currentTimeMillis())

            syncLogDao.insertLog(
                SyncLogEntity(
                    timestamp = System.currentTimeMillis(),
                    status = "SUCCESS",
                    itemsFound = newNotificationsCount,
                    newNotificationsCount = newNotificationsCount,
                    errorMessage = null
                )
            )

            newNotificationsCount
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("GitHubRepository", "Sync failed", e)
            val isAuthError = e is HttpException && e.code() == 401
            val isNetwork = e is IOException

            if (isAuthError) {
                tokenManager.setAuthError("Session expired or token revoked. Please sign in again.")
                _syncStatus.value = SyncStatus.Error("Session expired or token revoked")
            } else {
                _isOffline.value = isNetwork
                val errorMsg = if (isNetwork) "Offline: showing local data" else (e.message ?: "Sync error")
                _syncStatus.value = if (isNetwork) SyncStatus.Offline(errorMsg) else SyncStatus.Error(errorMsg)
            }

            syncLogDao.insertLog(
                SyncLogEntity(
                    timestamp = System.currentTimeMillis(),
                    status = "FAILED",
                    itemsFound = 0,
                    newNotificationsCount = 0,
                    errorMessage = if (isAuthError) "Session expired or token revoked" else if (isNetwork) "Offline" else (e.message ?: "Sync error")
                )
            )

            0
        }
    }

    suspend fun getAssistantSummary(): AssistantSummary = withContext(Dispatchers.IO) {
        val unread = notificationDao.getUnreadNotifications()

        var pendingReviews = 0
        var assignedIssues = 0
        var failedWorkflows = 0
        var unreadNotifications = unread.size

        val actionableItems = mutableListOf<GitHubNotificationEntity>()

        for (item in unread) {
            when {
                item.category == "WORKFLOW" && (item.actionState == "FAILURE" || item.actionState == "FAILED") -> {
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

        val total = pendingReviews + assignedIssues + failedWorkflows

        AssistantSummary(
            pendingReviewRequests = pendingReviews,
            assignedIssues = assignedIssues,
            failedWorkflows = failedWorkflows,
            unreadNotifications = unreadNotifications,
            totalActionableItems = total,
            topActionableItems = actionableItems
        )
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        notificationDao.clearAllNotifications()
        processedEventDao.clearProcessedEvents()
        syncLogDao.clearLogs()
    }

    suspend fun createTestNotification(context: Context) = withContext(Dispatchers.IO) {
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
        NotificationHelper.postNotification(context, testNotif, preferencesRepository.notificationPrefs.value)
    }

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
}


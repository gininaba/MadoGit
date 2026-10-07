package com.aipos.madogit

import com.aipos.madogit.data.api.GitHubApiService
import com.aipos.madogit.data.api.models.GitHubEventDto
import com.aipos.madogit.data.api.models.GitHubIssueDto
import com.aipos.madogit.data.api.models.GitHubNotificationDto
import com.aipos.madogit.data.api.models.GitHubPullRequestDto
import com.aipos.madogit.data.api.models.GitHubRateLimitResponse
import com.aipos.madogit.data.api.models.GitHubReleaseDto
import com.aipos.madogit.data.api.models.GitHubRepoDto
import com.aipos.madogit.data.api.models.GitHubUserDto
import com.aipos.madogit.data.api.models.GitHubWorkflowRunDto
import com.aipos.madogit.data.api.models.GitHubWorkflowRunsResponse
import com.aipos.madogit.data.api.models.NotificationRepoDto
import com.aipos.madogit.data.api.models.OAuthTokenResponse
import com.aipos.madogit.data.api.models.RateLimitDto
import com.aipos.madogit.data.api.models.RateLimitResourcesDto
import com.aipos.madogit.data.api.models.SubjectDto
import com.aipos.madogit.data.api.models.UserSummaryDto
import com.aipos.madogit.data.database.entities.GitHubNotificationEntity
import com.aipos.madogit.data.repository.NotificationPreferences
import com.aipos.madogit.notifications.NotificationDispatcher
import kotlinx.coroutines.delay
import retrofit2.Response
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** In-memory fake of the GitHub REST API used to drive the sync engine deterministically. */
class FakeGitHubApiService : GitHubApiService {
    var user = GitHubUserDto(
        id = 1L, login = "me", name = "Me", avatarUrl = "https://avatars/me",
        bio = null, htmlUrl = "https://github.com/me"
    )
    var repos: List<GitHubRepoDto> = emptyList()
    var notifications: List<GitHubNotificationDto> = emptyList()
    var workflowRuns: Map<String, List<GitHubWorkflowRunDto>> = emptyMap()
    var pullRequests: Map<String, List<GitHubPullRequestDto>> = emptyMap()
    var issues: Map<String, List<GitHubIssueDto>> = emptyMap()
    var releases: Map<String, List<GitHubReleaseDto>> = emptyMap()
    var rateRemaining = 5000
    var rateLimitDelayMs = 0L
    var rateLimitError: Exception? = null

    val notificationsCalls = AtomicInteger(0)
    val pullRequestCalls = AtomicInteger(0)
    val workflowCalls = AtomicInteger(0)
    val repoPagesRequested = CopyOnWriteArrayList<Int>()
    val markedThreads = CopyOnWriteArrayList<String>()
    var lastPullRequestState: String? = null

    override suspend fun getCurrentUser(): GitHubUserDto = user

    override suspend fun getUserRepos(perPage: Int, sort: String, type: String, page: Int): List<GitHubRepoDto> {
        repoPagesRequested.add(page)
        return repos.drop((page - 1) * perPage).take(perPage)
    }

    override suspend fun getNotifications(all: Boolean, participating: Boolean): List<GitHubNotificationDto> {
        notificationsCalls.incrementAndGet()
        return notifications
    }

    override suspend fun markNotificationAsRead(threadId: String, body: Map<String, String>): Response<Unit> {
        markedThreads.add(threadId)
        return Response.success(Unit)
    }

    override suspend fun markAllNotificationsAsRead(body: Map<String, Boolean>): Response<Unit> =
        Response.success(Unit)

    override suspend fun getRepoEvents(owner: String, repo: String, perPage: Int): List<GitHubEventDto> = emptyList()

    override suspend fun getReceivedEvents(username: String, perPage: Int): List<GitHubEventDto> = emptyList()

    override suspend fun getPullRequests(
        owner: String, repo: String, state: String, perPage: Int, sort: String, direction: String
    ): List<GitHubPullRequestDto> {
        pullRequestCalls.incrementAndGet()
        lastPullRequestState = state
        return pullRequests["$owner/$repo"].orEmpty()
    }

    override suspend fun getIssues(owner: String, repo: String, state: String, perPage: Int): List<GitHubIssueDto> =
        issues["$owner/$repo"].orEmpty()

    override suspend fun getWorkflowRuns(owner: String, repo: String, perPage: Int): GitHubWorkflowRunsResponse {
        workflowCalls.incrementAndGet()
        val runs = workflowRuns["$owner/$repo"].orEmpty()
        return GitHubWorkflowRunsResponse(totalCount = runs.size, workflowRuns = runs)
    }

    override suspend fun getReleases(owner: String, repo: String, perPage: Int): List<GitHubReleaseDto> =
        releases["$owner/$repo"].orEmpty()

    override suspend fun getRateLimit(): GitHubRateLimitResponse {
        if (rateLimitDelayMs > 0) delay(rateLimitDelayMs)
        rateLimitError?.let { throw it }
        return GitHubRateLimitResponse(
            RateLimitResourcesDto(RateLimitDto(limit = 5000, remaining = rateRemaining, reset = 0L))
        )
    }

    override suspend fun exchangeOAuthToken(
        url: String, clientId: String, clientSecret: String, code: String, redirectUri: String
    ): OAuthTokenResponse = OAuthTokenResponse(null, null, null, "unsupported", null)

    companion object {
        fun owner(login: String = "octo") = UserSummaryDto(1L, login, "https://avatars/$login", "https://github.com/$login")

        fun repo(id: Long, fullName: String = "octo/repo$id") = GitHubRepoDto(
            id = id,
            name = fullName.substringAfter('/'),
            fullName = fullName,
            private = false,
            description = null,
            htmlUrl = "https://github.com/$fullName",
            updatedAt = null,
            owner = owner(fullName.substringBefore('/'))
        )

        fun thread(
            id: String,
            type: String,
            subjectUrl: String?,
            repoFullName: String = "octo/repo1",
            unread: Boolean = true,
            reason: String = "subscribed",
            title: String = "Thread $id",
            updatedAt: String = "2026-10-01T10:00:00Z"
        ) = GitHubNotificationDto(
            id = id,
            unread = unread,
            reason = reason,
            updatedAt = updatedAt,
            subject = SubjectDto(title = title, url = subjectUrl, latestCommentUrl = null, type = type),
            repository = NotificationRepoDto(
                id = 1L,
                name = repoFullName.substringAfter('/'),
                fullName = repoFullName,
                htmlUrl = "https://github.com/$repoFullName",
                private = false,
                owner = owner(repoFullName.substringBefore('/'))
            )
        )

        fun run(id: Long, conclusion: String?, status: String = "completed", runNumber: Int = id.toInt()) = GitHubWorkflowRunDto(
            id = id,
            name = "CI",
            status = status,
            conclusion = conclusion,
            htmlUrl = "https://github.com/octo/repo1/actions/runs/$id",
            createdAt = "2026-10-01T10:00:00Z",
            updatedAt = "2026-10-01T10:05:00Z",
            headBranch = "main",
            event = "push",
            runNumber = runNumber
        )

        fun pr(id: Long, number: Int = id.toInt(), state: String = "open", mergedAt: String? = null, repoFullName: String = "octo/repo1") =
            GitHubPullRequestDto(
                id = id,
                number = number,
                title = "Change $number",
                state = state,
                htmlUrl = "https://github.com/$repoFullName/pull/$number",
                createdAt = "2026-10-01T10:00:00Z",
                updatedAt = "2026-10-01T11:00:00Z",
                user = owner("contributor"),
                mergedAt = mergedAt
            )
    }
}

/** Records dispatched / cancelled notifications instead of hitting NotificationManager. */
class RecordingDispatcher : NotificationDispatcher {
    val posted = CopyOnWriteArrayList<GitHubNotificationEntity>()
    val cancelled = CopyOnWriteArrayList<String>()
    val cancelAllCalls = AtomicInteger(0)

    override fun post(notification: GitHubNotificationEntity, prefs: NotificationPreferences) {
        posted.add(notification)
    }

    override fun cancel(notificationId: String) {
        cancelled.add(notificationId)
    }

    override fun cancelAll() {
        cancelAllCalls.incrementAndGet()
    }
}

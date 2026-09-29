package com.example.data.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUserDto(
    val id: Long,
    val login: String,
    val name: String?,
    @Json(name = "avatar_url") val avatarUrl: String,
    val bio: String?,
    @Json(name = "public_repos") val publicRepos: Int = 0,
    @Json(name = "total_private_repos") val totalPrivateRepos: Int = 0,
    @Json(name = "html_url") val htmlUrl: String
)

@JsonClass(generateAdapter = true)
data class UserSummaryDto(
    val id: Long,
    val login: String,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "html_url") val htmlUrl: String?
)

@JsonClass(generateAdapter = true)
data class GitHubRepoDto(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val private: Boolean,
    val description: String?,
    @Json(name = "stargazers_count") val stargazersCount: Int = 0,
    @Json(name = "forks_count") val forksCount: Int = 0,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "updated_at") val updatedAt: String?,
    val owner: UserSummaryDto
)

@JsonClass(generateAdapter = true)
data class GitHubNotificationDto(
    val id: String,
    val unread: Boolean,
    val reason: String,
    @Json(name = "updated_at") val updatedAt: String,
    val subject: SubjectDto,
    val repository: NotificationRepoDto
)

@JsonClass(generateAdapter = true)
data class SubjectDto(
    val title: String,
    val url: String?,
    @Json(name = "latest_comment_url") val latestCommentUrl: String?,
    val type: String
)

@JsonClass(generateAdapter = true)
data class NotificationRepoDto(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "html_url") val htmlUrl: String?,
    val private: Boolean,
    val owner: UserSummaryDto
)

@JsonClass(generateAdapter = true)
data class GitHubEventDto(
    val id: String,
    val type: String,
    val actor: UserSummaryDto,
    val repo: EventRepoDto,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class EventRepoDto(
    val id: Long,
    val name: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class GitHubPullRequestDto(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    val user: UserSummaryDto,
    @Json(name = "requested_reviewers") val requestedReviewers: List<UserSummaryDto>? = emptyList(),
    val draft: Boolean = false
)

@JsonClass(generateAdapter = true)
data class IssuePullRequestRefDto(
    val url: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubIssueDto(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    val user: UserSummaryDto,
    val assignees: List<UserSummaryDto>? = emptyList(),
    @Json(name = "pull_request") val pullRequest: IssuePullRequestRefDto? = null
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowRunsResponse(
    @Json(name = "total_count") val totalCount: Int,
    @Json(name = "workflow_runs") val workflowRuns: List<GitHubWorkflowRunDto>
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowRunDto(
    val id: Long,
    val name: String?,
    val status: String?,
    val conclusion: String?,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "head_branch") val headBranch: String?,
    val event: String?,
    @Json(name = "run_number") val runNumber: Int
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    val id: Long,
    @Json(name = "tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "published_at") val publishedAt: String?,
    val prerelease: Boolean = false,
    val author: UserSummaryDto?
)

@JsonClass(generateAdapter = true)
data class GitHubRateLimitResponse(
    val resources: RateLimitResourcesDto
)

@JsonClass(generateAdapter = true)
data class RateLimitResourcesDto(
    val core: RateLimitDto
)

@JsonClass(generateAdapter = true)
data class RateLimitDto(
    val limit: Int,
    val remaining: Int,
    val reset: Long,
    val used: Int = 0
)

@JsonClass(generateAdapter = true)
data class OAuthTokenResponse(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type") val tokenType: String?,
    val scope: String?,
    val error: String?,
    @Json(name = "error_description") val errorDescription: String?
)

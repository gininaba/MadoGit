package com.example.data.api

import com.example.data.api.models.GitHubEventDto
import com.example.data.api.models.GitHubIssueDto
import com.example.data.api.models.GitHubNotificationDto
import com.example.data.api.models.GitHubPullRequestDto
import com.example.data.api.models.GitHubRateLimitResponse
import com.example.data.api.models.GitHubReleaseDto
import com.example.data.api.models.GitHubRepoDto
import com.example.data.api.models.GitHubUserDto
import com.example.data.api.models.GitHubWorkflowRunsResponse
import com.example.data.api.models.OAuthTokenResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface GitHubApiService {

    @GET("user")
    suspend fun getCurrentUser(): GitHubUserDto

    @GET("user/repos")
    suspend fun getUserRepos(
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated",
        @Query("type") type: String = "all"
    ): List<GitHubRepoDto>

    @GET("notifications")
    suspend fun getNotifications(
        @Query("all") all: Boolean = true,
        @Query("participating") participating: Boolean = false
    ): List<GitHubNotificationDto>

    @PATCH("notifications/threads/{thread_id}")
    suspend fun markNotificationAsRead(
        @Path("thread_id") threadId: String
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/events")
    suspend fun getRepoEvents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30
    ): List<GitHubEventDto>

    @GET("users/{username}/received_events")
    suspend fun getReceivedEvents(
        @Path("username") username: String,
        @Query("per_page") perPage: Int = 30
    ): List<GitHubEventDto>

    @GET("repos/{owner}/{repo}/pulls")
    suspend fun getPullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "all",
        @Query("per_page") perPage: Int = 20
    ): List<GitHubPullRequestDto>

    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "all",
        @Query("per_page") perPage: Int = 20
    ): List<GitHubIssueDto>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20
    ): GitHubWorkflowRunsResponse

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 10
    ): List<GitHubReleaseDto>

    @GET("rate_limit")
    suspend fun getRateLimit(): GitHubRateLimitResponse

    @POST
    @Headers("Accept: application/json")
    suspend fun exchangeOAuthToken(
        @Url url: String = "https://github.com/login/oauth/access_token",
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
        @Query("code") code: String,
        @Query("redirect_uri") redirectUri: String
    ): OAuthTokenResponse
}

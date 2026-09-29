package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.data.api.models.GitHubUserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data object Loading : AuthState()
    data class Authenticated(
        val token: String,
        val username: String,
        val avatarUrl: String,
        val displayName: String? = null
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gh_auth_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AUTH_TOKEN = "github_access_token"
        private const val KEY_OAUTH_CLIENT_ID = "github_oauth_client_id"
        private const val KEY_OAUTH_CLIENT_SECRET = "github_oauth_client_secret"
        private const val KEY_REDIRECT_URI = "github_oauth_redirect_uri"
        private const val KEY_USERNAME = "cached_username"
        private const val KEY_AVATAR_URL = "cached_avatar_url"
        private const val KEY_DISPLAY_NAME = "cached_display_name"
        private const val DEFAULT_REDIRECT_URI = "ghnotifier://oauth/callback"
    }

    private val _authState = MutableStateFlow<AuthState>(loadInitialAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private fun loadInitialAuthState(): AuthState {
        val token = prefs.getString(KEY_AUTH_TOKEN, null)
        val username = prefs.getString(KEY_USERNAME, null)
        val avatarUrl = prefs.getString(KEY_AVATAR_URL, null)
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null)

        return if (!token.isNullOrBlank() && !username.isNullOrBlank()) {
            AuthState.Authenticated(
                token = token,
                username = username,
                avatarUrl = avatarUrl ?: "",
                displayName = displayName
            )
        } else {
            AuthState.Unauthenticated
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_AUTH_TOKEN, null)

    fun getOAuthClientId(): String = prefs.getString(KEY_OAUTH_CLIENT_ID, "") ?: ""

    fun getOAuthClientSecret(): String = prefs.getString(KEY_OAUTH_CLIENT_SECRET, "") ?: ""

    fun getRedirectUri(): String = prefs.getString(KEY_REDIRECT_URI, DEFAULT_REDIRECT_URI) ?: DEFAULT_REDIRECT_URI

    fun saveOAuthConfiguration(clientId: String, clientSecret: String, redirectUri: String = DEFAULT_REDIRECT_URI) {
        prefs.edit()
            .putString(KEY_OAUTH_CLIENT_ID, clientId.trim())
            .putString(KEY_OAUTH_CLIENT_SECRET, clientSecret.trim())
            .putString(KEY_REDIRECT_URI, redirectUri.trim())
            .apply()
    }

    fun saveAuthSuccess(token: String, user: GitHubUserDto) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token.trim())
            .putString(KEY_USERNAME, user.login)
            .putString(KEY_AVATAR_URL, user.avatarUrl)
            .putString(KEY_DISPLAY_NAME, user.name ?: user.login)
            .apply()

        _authState.value = AuthState.Authenticated(
            token = token.trim(),
            username = user.login,
            avatarUrl = user.avatarUrl,
            displayName = user.name ?: user.login
        )
    }

    fun updateCachedUser(user: GitHubUserDto) {
        val token = getAccessToken() ?: return
        prefs.edit()
            .putString(KEY_USERNAME, user.login)
            .putString(KEY_AVATAR_URL, user.avatarUrl)
            .putString(KEY_DISPLAY_NAME, user.name ?: user.login)
            .apply()

        _authState.value = AuthState.Authenticated(
            token = token,
            username = user.login,
            avatarUrl = user.avatarUrl,
            displayName = user.name ?: user.login
        )
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_AVATAR_URL)
            .remove(KEY_DISPLAY_NAME)
            .apply()

        _authState.value = AuthState.Unauthenticated
    }

    fun setAuthLoading() {
        _authState.value = AuthState.Loading
    }

    fun setAuthError(message: String) {
        _authState.value = AuthState.Error(message)
    }
}

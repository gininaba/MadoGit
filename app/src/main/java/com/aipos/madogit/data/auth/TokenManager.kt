package com.aipos.madogit.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.aipos.madogit.data.api.models.GitHubUserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

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
        private const val KEY_OAUTH_STATE = "github_oauth_state"
        private const val KEY_USERNAME = "cached_username"
        private const val KEY_AVATAR_URL = "cached_avatar_url"
        private const val KEY_DISPLAY_NAME = "cached_display_name"
        private const val DEFAULT_REDIRECT_URI = "ghnotifier://oauth/callback"
    }

    private val _authState = MutableStateFlow<AuthState>(loadInitialAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private fun loadInitialAuthState(): AuthState {
        val encryptedToken = prefs.getString(KEY_AUTH_TOKEN, null)
        val token = if (!encryptedToken.isNullOrBlank()) CryptoManager.decrypt(encryptedToken) else null
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

    fun getAccessToken(): String? {
        val rawOrEncrypted = prefs.getString(KEY_AUTH_TOKEN, null) ?: return null
        val decrypted = CryptoManager.decrypt(rawOrEncrypted)
        return decrypted.ifBlank { null }
    }

    fun getOAuthClientId(): String = prefs.getString(KEY_OAUTH_CLIENT_ID, "") ?: ""

    fun getOAuthClientSecret(): String {
        val rawOrEncrypted = prefs.getString(KEY_OAUTH_CLIENT_SECRET, "") ?: ""
        return if (rawOrEncrypted.isNotBlank()) CryptoManager.decrypt(rawOrEncrypted) else ""
    }

    fun getRedirectUri(): String = prefs.getString(KEY_REDIRECT_URI, DEFAULT_REDIRECT_URI) ?: DEFAULT_REDIRECT_URI

    fun generateOAuthState(): String {
        val state = UUID.randomUUID().toString()
        prefs.edit { putString(KEY_OAUTH_STATE, state) }
        return state
    }

    fun verifyOAuthState(state: String?): Boolean {
        if (state.isNullOrBlank()) return false
        val savedState = prefs.getString(KEY_OAUTH_STATE, null)
        val matches = savedState != null && savedState == state
        // Clear once verified to prevent replay
        prefs.edit { remove(KEY_OAUTH_STATE) }
        return matches
    }

    fun saveOAuthConfiguration(clientId: String, clientSecret: String, redirectUri: String = DEFAULT_REDIRECT_URI) {
        val encryptedSecret = if (clientSecret.isNotBlank()) CryptoManager.encrypt(clientSecret.trim()) else ""
        prefs.edit {
            putString(KEY_OAUTH_CLIENT_ID, clientId.trim())
            putString(KEY_OAUTH_CLIENT_SECRET, encryptedSecret)
            putString(KEY_REDIRECT_URI, redirectUri.trim())
        }
    }

    fun saveAuthSuccess(token: String, user: GitHubUserDto) {
        val trimmedToken = token.trim()
        val encryptedToken = CryptoManager.encrypt(trimmedToken)

        prefs.edit {
            putString(KEY_AUTH_TOKEN, encryptedToken)
            putString(KEY_USERNAME, user.login)
            putString(KEY_AVATAR_URL, user.avatarUrl)
            putString(KEY_DISPLAY_NAME, user.name ?: user.login)
        }

        _authState.value = AuthState.Authenticated(
            token = trimmedToken,
            username = user.login,
            avatarUrl = user.avatarUrl,
            displayName = user.name ?: user.login
        )
    }

    fun updateCachedUser(user: GitHubUserDto) {
        val token = getAccessToken() ?: return
        prefs.edit {
            putString(KEY_USERNAME, user.login)
            putString(KEY_AVATAR_URL, user.avatarUrl)
            putString(KEY_DISPLAY_NAME, user.name ?: user.login)
        }

        _authState.value = AuthState.Authenticated(
            token = token,
            username = user.login,
            avatarUrl = user.avatarUrl,
            displayName = user.name ?: user.login
        )
    }

    fun clearAuth() {
        prefs.edit {
            remove(KEY_AUTH_TOKEN)
            remove(KEY_USERNAME)
            remove(KEY_AVATAR_URL)
            remove(KEY_DISPLAY_NAME)
            remove(KEY_OAUTH_STATE)
        }

        _authState.value = AuthState.Unauthenticated
    }

    fun setAuthLoading() {
        _authState.value = AuthState.Loading
    }

    fun setAuthError(message: String) {
        _authState.value = AuthState.Error(message)
    }
}


package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.example.data.repository.ThemeMode
import com.example.ui.MainViewModel
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.GitHubNotifierTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as GitHubNotifierApp
        viewModel = ViewModelProvider(
            this,
            MainViewModel.provideFactory(
                app.repository,
                app.tokenManager,
                app.preferencesRepository
            )
        )[MainViewModel::class.java]

        handleIntent(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val dynamicColor by viewModel.dynamicColor.collectAsState()
            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            GitHubNotifierTheme(darkTheme = isDark, dynamicColor = dynamicColor) {
                AppNavigation(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        // Check for OAuth redirect: ghnotifier://oauth/callback?code=...&state=...
        if (data.scheme == "ghnotifier" && data.host == "oauth" && data.path == "/callback") {
            val code = data.getQueryParameter("code")
            val state = data.getQueryParameter("state")
            if (!code.isNullOrBlank()) {
                viewModel.handleOAuthCode(code, state) { success, error ->
                    if (success) {
                        Toast.makeText(this, "GitHub connected successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "OAuth failed: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}


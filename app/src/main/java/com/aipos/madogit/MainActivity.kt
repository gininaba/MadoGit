package com.aipos.madogit

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.aipos.madogit.data.repository.ThemeMode
import com.aipos.madogit.ui.MainViewModel
import com.aipos.madogit.ui.navigation.AppNavigation
import com.aipos.madogit.ui.splash.SplashScreen
import com.aipos.madogit.ui.theme.GitHubNotifierTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    companion object {
        private var hasShownSplash = false
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        val app = application as GitHubNotifierApp
        viewModel = ViewModelProvider(
            this,
            MainViewModel.provideFactory(
                app.repository,
                app.tokenManager,
                app.preferencesRepository
            )
        )[MainViewModel::class.java]

        // Only consume the launch intent on a fresh start; after a configuration change or process
        // recreation the same intent is redelivered and its one-shot OAuth code is already spent.
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val dynamicColor by viewModel.dynamicColor.collectAsState()
            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }

            var showSplash by remember { mutableStateOf(!hasShownSplash) }

            LaunchedEffect(Unit) {
                if (showSplash) {
                    delay(1000)
                    showSplash = false
                    hasShownSplash = true
                }
            }

            GitHubNotifierTheme(darkTheme = isDark, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = showSplash,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                        },
                        label = "SplashTransition"
                    ) { isSplashing ->
                        if (isSplashing) {
                            SplashScreen(isDark = isDark)
                        } else {
                            AppNavigation(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        // "View in App" action from a system notification
        intent.getStringExtra(EXTRA_NOTIFICATION_ID)?.let { notificationId ->
            intent.removeExtra(EXTRA_NOTIFICATION_ID)
            viewModel.openNotificationFromSystem(notificationId)
        }

        val data = intent.data ?: return
        // Check for OAuth redirect: ghnotifier://oauth/callback?code=...&state=...
        if (data.scheme == "ghnotifier" && data.host == "oauth" && data.path == "/callback") {
            intent.data = null // consume: never exchange the same single-use code twice
            val code = data.getQueryParameter("code")
            val state = data.getQueryParameter("state")
            val error = data.getQueryParameter("error")
            when {
                !code.isNullOrBlank() -> {
                    viewModel.handleOAuthCode(code, state) { success, errorMessage ->
                        if (success) {
                            Toast.makeText(this, "GitHub connected successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "OAuth failed: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                // e.g. error=access_denied when the user cancels on GitHub's consent page
                !error.isNullOrBlank() -> {
                    val description = data.getQueryParameter("error_description") ?: error.replace('_', ' ')
                    viewModel.reportOAuthError("GitHub authorization was not completed: $description")
                    Toast.makeText(this, "OAuth cancelled: $description", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}


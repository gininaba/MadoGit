package com.example.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.AuthState
import com.example.ui.MainViewModel
import com.example.ui.assistant.AssistantScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.notifications.NotificationsScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.repositories.RepositoriesScreen
import com.example.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val authState by viewModel.authState.collectAsState()
    val isFirstLaunch by viewModel.isFirstLaunch.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val assistantSummary by viewModel.assistantSummary.collectAsState()

    var currentDestination by rememberSaveable { mutableStateOf(NavDestination.DASHBOARD) }

    // If first launch, show onboarding
    if (isFirstLaunch && authState !is AuthState.Authenticated) {
        OnboardingScreen(
            viewModel = viewModel,
            onComplete = {
                currentDestination = NavDestination.DASHBOARD
            },
            modifier = modifier
        )
        return
    }

    // If unauthenticated after first launch (e.g. signed out), show AuthScreen directly
    if (authState !is AuthState.Authenticated) {
        AuthScreen(
            viewModel = viewModel,
            onAuthSuccess = {
                currentDestination = NavDestination.DASHBOARD
            },
            modifier = modifier
        )
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        NavDestination.entries.forEach { destination ->
                            val isSelected = currentDestination == destination
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentDestination = destination },
                                label = { Text(destination.title, fontSize = 11.sp, fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal) },
                                icon = {
                                    when {
                                        destination == NavDestination.NOTIFICATIONS && unreadCount > 0 -> {
                                            BadgedBox(
                                                badge = {
                                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                                        Text("$unreadCount", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp)
                                                    }
                                                }
                                            ) {
                                                Icon(destination.icon, contentDescription = destination.title)
                                            }
                                        }
                                        destination == NavDestination.ASSISTANT && assistantSummary.totalActionableItems > 0 -> {
                                            BadgedBox(
                                                badge = {
                                                    Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                                        Text("${assistantSummary.totalActionableItems}", color = MaterialTheme.colorScheme.onTertiary, fontSize = 10.sp)
                                                    }
                                                }
                                            ) {
                                                Icon(destination.icon, contentDescription = destination.title)
                                            }
                                        }
                                        else -> {
                                            Icon(destination.icon, contentDescription = destination.title)
                                        }
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier.testTag("nav_item_${destination.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (isExpanded) {
                // Adaptive wide layout: NavigationRail on the left
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NavDestination.entries.forEach { destination ->
                            val isSelected = currentDestination == destination
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { currentDestination = destination },
                                label = { Text(destination.title, fontSize = 11.sp, fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal) },
                                icon = { Icon(destination.icon, contentDescription = destination.title) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ScreenContent(
                        destination = currentDestination,
                        viewModel = viewModel,
                        onNavigate = { currentDestination = it }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ScreenContent(
                    destination = currentDestination,
                    viewModel = viewModel,
                    onNavigate = { currentDestination = it }
                )
            }
        }
    }
}
}

@Composable
private fun ScreenContent(
    destination: NavDestination,
    viewModel: MainViewModel,
    onNavigate: (NavDestination) -> Unit
) {
    Crossfade(targetState = destination, label = "screen_crossfade") { target ->
        when (target) {
            NavDestination.DASHBOARD -> DashboardScreen(
                viewModel = viewModel,
                onNavigateToNotifications = { onNavigate(NavDestination.NOTIFICATIONS) },
                onNavigateToRepositories = { onNavigate(NavDestination.REPOSITORIES) },
                onNavigateToAssistant = { onNavigate(NavDestination.ASSISTANT) }
            )
            NavDestination.NOTIFICATIONS -> NotificationsScreen(viewModel = viewModel)
            NavDestination.REPOSITORIES -> RepositoriesScreen(viewModel = viewModel)
            NavDestination.ASSISTANT -> AssistantScreen(viewModel = viewModel)
            NavDestination.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                onSignOut = { onNavigate(NavDestination.DASHBOARD) }
            )
        }
    }
}

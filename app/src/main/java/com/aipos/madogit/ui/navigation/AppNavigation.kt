package com.aipos.madogit.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aipos.madogit.data.auth.AuthState
import com.aipos.madogit.ui.MainViewModel
import com.aipos.madogit.ui.assistant.AssistantScreen
import com.aipos.madogit.ui.auth.AuthScreen
import com.aipos.madogit.ui.dashboard.DashboardScreen
import com.aipos.madogit.ui.notifications.NotificationsScreen
import com.aipos.madogit.ui.onboarding.OnboardingScreen
import com.aipos.madogit.ui.repositories.RepositoriesScreen
import com.aipos.madogit.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
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
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp
                    ) {
                        NavDestination.entries.forEach { destination ->
                            val isSelected = currentDestination == destination
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (currentDestination != destination) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentDestination = destination
                                    }
                                },
                                label = {
                                    Text(
                                        text = destination.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                                    )
                                },
                                icon = {
                                    when {
                                        destination == NavDestination.NOTIFICATIONS && unreadCount > 0 -> {
                                            BadgedBox(
                                                badge = {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ) {
                                                        Text("$unreadCount", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                                    }
                                                }
                                            ) {
                                                Icon(destination.icon, contentDescription = destination.title)
                                            }
                                        }
                                        destination == NavDestination.ASSISTANT && assistantSummary.totalActionableItems > 0 -> {
                                            BadgedBox(
                                                badge = {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                                    ) {
                                                        Text("${assistantSummary.totalActionableItems}", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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
                                onClick = {
                                    if (currentDestination != destination) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentDestination = destination
                                    }
                                },
                                label = {
                                    Text(
                                        text = destination.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                                    )
                                },
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
    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            if (forward) {
                (slideInHorizontally { width -> width / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { width -> -width / 4 } + fadeOut())
            } else {
                (slideInHorizontally { width -> -width / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { width -> width / 4 } + fadeOut())
            }
        },
        label = "screen_animated_content"
    ) { target ->
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


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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aipos.madogit.data.auth.AuthState
import com.aipos.madogit.data.repository.SyncStatus
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

    // Onboarding is pinned once started so that signing in on step 1 advances to the completion step
    // instead of immediately swapping the whole flow out for the dashboard.
    var onboardingInProgress by rememberSaveable {
        mutableStateOf(isFirstLaunch && authState !is AuthState.Authenticated)
    }

    // Users who were already signed in (e.g. upgraded installs) never need onboarding again.
    LaunchedEffect(isFirstLaunch, authState, onboardingInProgress) {
        if (isFirstLaunch && authState is AuthState.Authenticated && !onboardingInProgress) {
            viewModel.completeFirstLaunch()
        }
    }

    // Navigation requested from outside the UI (e.g. "View in App" on a system notification).
    val pendingNavigation by viewModel.pendingNavigation.collectAsState()
    val isSignedIn = authState is AuthState.Authenticated
    LaunchedEffect(pendingNavigation, isSignedIn, onboardingInProgress) {
        val target = pendingNavigation
        if (target != null && isSignedIn && !onboardingInProgress) {
            currentDestination = target
            viewModel.consumePendingNavigation()
        }
    }

    if (onboardingInProgress) {
        OnboardingScreen(
            viewModel = viewModel,
            onComplete = {
                onboardingInProgress = false
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

    // App-wide transient messages: repository refresh failures and reduced-polling notices.
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage by viewModel.userMessage.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    LaunchedEffect(userMessage) {
        userMessage?.let {
            viewModel.consumeUserMessage()
            snackbarHostState.showSnackbar(it)
        }
    }
    LaunchedEffect(syncStatus) {
        (syncStatus as? SyncStatus.Success)?.notice?.let { snackbarHostState.showSnackbar(it) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                    DestinationIcon(destination, unreadCount, assistantSummary.totalActionableItems)
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
                                icon = { DestinationIcon(destination, unreadCount, assistantSummary.totalActionableItems) },
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
private fun DestinationIcon(destination: NavDestination, unreadCount: Int, actionableCount: Int) {
    val badgeCount = when (destination) {
        NavDestination.NOTIFICATIONS -> unreadCount
        NavDestination.ASSISTANT -> actionableCount
        else -> 0
    }
    if (badgeCount <= 0) {
        Icon(destination.icon, contentDescription = destination.title)
        return
    }
    val isAssistant = destination == NavDestination.ASSISTANT
    BadgedBox(
        badge = {
            Badge(
                containerColor = if (isAssistant) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                contentColor = if (isAssistant) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                    fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    ) {
        Icon(destination.icon, contentDescription = "${destination.title}, $badgeCount new")
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


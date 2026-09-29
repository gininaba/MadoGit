package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", "Home", Icons.Default.Dashboard),
    NOTIFICATIONS("notifications", "Notifications", Icons.Default.Notifications),
    REPOSITORIES("repositories", "Repositories", Icons.Default.Source),
    ASSISTANT("assistant", "Assistant", Icons.Default.AutoAwesome),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

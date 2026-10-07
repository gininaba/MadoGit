package com.aipos.madogit.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aipos.madogit.data.auth.AuthState
import com.aipos.madogit.data.repository.ThemeMode
import com.aipos.madogit.ui.MainViewModel
import com.aipos.madogit.ui.components.RateLimitGauge
import com.aipos.madogit.ui.components.openExternalUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val notificationPrefs by viewModel.notificationPrefs.collectAsState()
    val syncPrefs by viewModel.syncPrefs.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val rateLimitInfo by viewModel.rateLimitInfo.collectAsState()
    val lastSyncTime by viewModel.lastSyncTimestamp.collectAsState()
    val monitoredCount by viewModel.monitoredCount.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. ACCOUNT SECTION
            item {
                SettingsSectionCard(title = "GitHub Account", icon = Icons.Default.Security) {
                    when (val state = authState) {
                        is AuthState.Authenticated -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.displayName ?: state.username,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "@${state.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Button(
                                    onClick = {
                                        viewModel.disconnect()
                                        onSignOut()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    shape = MaterialTheme.shapes.small,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("sign_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "No GitHub account connected.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = onSignOut,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = MaterialTheme.shapes.small,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("connect_account_button")
                                ) {
                                    Text("Connect Account", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // 2. APPEARANCE (MATERIAL YOU)
            item {
                SettingsSectionCard(title = "Appearance & Material You", icon = Icons.Default.Palette) {
                    Text(
                        text = "Customize the theme mode and wallpaper-driven dynamic coloring.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val modes = listOf(
                        Pair(ThemeMode.LIGHT, "Light Mode"),
                        Pair(ThemeMode.DARK, "Dark Mode"),
                        Pair(ThemeMode.SYSTEM, "System Default")
                    )

                    modes.forEach { (mode, label) ->
                        val isSelected = themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .selectable(
                                    selected = isSelected,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Material You Dynamic Color",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Extract dynamic Monet palette from your device wallpaper",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { viewModel.setDynamicColor(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))

                    PalettePreviewRow()
                }
            }

            // 3. NOTIFICATIONS CONFIGURATION
            item {
                SettingsSectionCard(title = "Notification Preferences", icon = Icons.Default.Notifications) {
                    Text(
                        text = "Customize which GitHub events generate system push notifications on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Master: Pull Requests
                    CategoryMasterToggle(
                        title = "Pull Requests",
                        enabled = notificationPrefs.prMaster,
                        onToggle = {
                            viewModel.updateNotificationPreferences(notificationPrefs.copy(prMaster = it))
                        }
                    )
                    if (notificationPrefs.prMaster) {
                        Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                            SubOptionCheckbox("PR opened", notificationPrefs.prOpened) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(prOpened = it))
                            }
                            SubOptionCheckbox("Review requested from you", notificationPrefs.prReviewRequested) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(prReviewRequested = it))
                            }
                            SubOptionCheckbox("Review approved", notificationPrefs.prApproved) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(prApproved = it))
                            }
                            SubOptionCheckbox("Changes requested", notificationPrefs.prChangesRequested) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(prChangesRequested = it))
                            }
                            SubOptionCheckbox("PR merged", notificationPrefs.prMerged) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(prMerged = it))
                            }
                            SubOptionCheckbox("PR closed without merge", notificationPrefs.prClosed) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(prClosed = it))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))

                    // Master: Issues
                    CategoryMasterToggle(
                        title = "Issues & Discussions",
                        enabled = notificationPrefs.issueMaster,
                        onToggle = {
                            viewModel.updateNotificationPreferences(notificationPrefs.copy(issueMaster = it))
                        }
                    )
                    if (notificationPrefs.issueMaster) {
                        Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                            SubOptionCheckbox("New issue opened", notificationPrefs.issueOpened) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(issueOpened = it))
                            }
                            SubOptionCheckbox("Assigned to me", notificationPrefs.issueAssigned) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(issueAssigned = it))
                            }
                            SubOptionCheckbox("Mentioned in issue/comment", notificationPrefs.issueMentioned) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(issueMentioned = it))
                            }
                            SubOptionCheckbox("New comments on threads", notificationPrefs.issueCommented) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(issueCommented = it))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))

                    // Master: GitHub Actions
                    CategoryMasterToggle(
                        title = "GitHub Actions (CI/CD)",
                        enabled = notificationPrefs.actionMaster,
                        onToggle = {
                            viewModel.updateNotificationPreferences(notificationPrefs.copy(actionMaster = it))
                        }
                    )
                    if (notificationPrefs.actionMaster) {
                        Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                            SubOptionCheckbox("Workflow failed (critical)", notificationPrefs.actionFailed) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(actionFailed = it))
                            }
                            SubOptionCheckbox("Workflow succeeded", notificationPrefs.actionSucceeded) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(actionSucceeded = it))
                            }
                            SubOptionCheckbox("Workflow cancelled", notificationPrefs.actionCancelled) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(actionCancelled = it))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))

                    // Releases
                    CategoryMasterToggle(
                        title = "Releases published",
                        enabled = notificationPrefs.releaseMaster,
                        onToggle = {
                            viewModel.updateNotificationPreferences(notificationPrefs.copy(releaseMaster = it))
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))

                    // Master: Repository activity (commits, comments)
                    CategoryMasterToggle(
                        title = "Repository Activity",
                        enabled = notificationPrefs.activityMaster,
                        onToggle = {
                            viewModel.updateNotificationPreferences(notificationPrefs.copy(activityMaster = it))
                        }
                    )
                    if (notificationPrefs.activityMaster) {
                        Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                            SubOptionCheckbox("Commits pushed", notificationPrefs.activityCommits) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(activityCommits = it))
                            }
                            SubOptionCheckbox("Commit comments", notificationPrefs.activityComments) {
                                viewModel.updateNotificationPreferences(notificationPrefs.copy(activityComments = it))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))

                    // System alerts style
                    CategoryMasterToggle(
                        title = "Sound enabled",
                        enabled = notificationPrefs.soundEnabled,
                        onToggle = {
                            viewModel.updateNotificationPreferences(notificationPrefs.copy(soundEnabled = it))
                        }
                    )
                    CategoryMasterToggle(
                        title = "Vibration enabled",
                        enabled = notificationPrefs.vibrationEnabled,
                        onToggle = {
                            viewModel.updateNotificationPreferences(notificationPrefs.copy(vibrationEnabled = it))
                        }
                    )
                    CategoryMasterToggle(
                        title = "Group notifications",
                        enabled = notificationPrefs.groupingEnabled,
                        onToggle = {
                            viewModel.updateNotificationPreferences(notificationPrefs.copy(groupingEnabled = it))
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // System Notification Settings Shortcut
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open system notification settings", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("system_notification_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "System Notification Channels",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Test Notification Button
                    Button(
                        onClick = {
                            viewModel.sendTestNotification()
                            Toast.makeText(context, "Test notification dispatched!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_test_notification_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send Test Notification",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 4. BACKGROUND MONITORING SETTINGS
            item {
                SettingsSectionCard(title = "Background Monitoring", icon = Icons.Default.Schedule) {
                    Text(
                        text = "Android WorkManager periodically queries GitHub in the background according to battery and network conditions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Sync Frequency",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val intervals = listOf(
                        Pair(15L, "15 min (Recommended)"),
                        Pair(30L, "30 min"),
                        Pair(60L, "1 hour"),
                        Pair(120L, "2 hours"),
                        Pair(360L, "6 hours"),
                        Pair(0L, "Manual Only")
                    )

                    intervals.forEach { (minutes, label) ->
                        val isSelected = syncPrefs.intervalMinutes == minutes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    viewModel.updateSyncPreferences(syncPrefs.copy(intervalMinutes = minutes), context)
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(
                                        2.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))

                    CategoryMasterToggle(
                        title = "Sync on Wi-Fi Only",
                        enabled = syncPrefs.wifiOnly,
                        onToggle = {
                            viewModel.updateSyncPreferences(syncPrefs.copy(wifiOnly = it), context)
                        }
                    )
                    CategoryMasterToggle(
                        title = "Auto-monitor newly discovered repos",
                        enabled = syncPrefs.monitorAllByDefault,
                        onToggle = {
                            viewModel.updateSyncPreferences(syncPrefs.copy(monitorAllByDefault = it), context)
                        }
                    )
                }
            }

            // 5. DIAGNOSTICS & DEBUG
            item {
                SettingsSectionCard(title = "Diagnostics & API Quota", icon = Icons.Default.BugReport) {
                    RateLimitGauge(
                        remaining = rateLimitInfo.first,
                        limit = rateLimitInfo.second,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 10.dp))

                    DiagnosticItem("API Rate Limit Remaining", "${rateLimitInfo.first} / ${rateLimitInfo.second}")
                    DiagnosticItem("Monitored Repositories", "$monitoredCount repos")
                    DiagnosticItem(
                        "Last Synchronized",
                        if (lastSyncTime > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(lastSyncTime)) else "Never"
                    )
                    DiagnosticItem("Background Engine", "WorkManager (Periodic)")

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.clearCache()
                            Toast.makeText(context, "Local cache & logs cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Local Cache & Event History", fontSize = 12.sp)
                    }
                }
            }

            // 6. ABOUT
            item {
                SettingsSectionCard(title = "About", icon = Icons.Default.Info) {
                    Text(
                        text = "MadoGit v${com.aipos.madogit.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mado (窓 = window) — A minimal window into your GitHub activity. Built with Jetpack Compose Material You, Room persistence, and Android WorkManager.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))

                    // Developer Attribution
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { openExternalUrl(context, "https://github.com/gininaba") }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Developed by gininaba",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "github.com/gininaba",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Developer Profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Project Repository
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { openExternalUrl(context, "https://github.com/gininaba/MadoGit") }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Source Code & Feedback",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "github.com/gininaba/MadoGit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Repository",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status & License Tags
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "Experimental",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "Apache 2.0 License",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MadoGit • Developed by gininaba",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

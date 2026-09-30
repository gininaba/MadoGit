package com.aipos.madogit.ui.dashboard

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aipos.madogit.data.auth.AuthState
import com.aipos.madogit.data.database.entities.GitHubNotificationEntity
import com.aipos.madogit.data.repository.SyncStatus
import com.aipos.madogit.ui.MainViewModel
import com.aipos.madogit.ui.components.EmptyStateView
import com.aipos.madogit.ui.components.MadoPullToRefreshBox
import com.aipos.madogit.ui.components.OfflineBanner
import com.aipos.madogit.ui.components.RateLimitGauge
import com.aipos.madogit.ui.components.StatusBadge
import com.aipos.madogit.ui.components.SyncButton
import com.aipos.madogit.ui.components.formatRelativeTime
import com.aipos.madogit.ui.components.openExternalUrl

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToNotifications: () -> Unit,
    onNavigateToRepositories: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val monitoredCount by viewModel.monitoredCount.collectAsState()
    val assistantSummary by viewModel.assistantSummary.collectAsState()
    val allNotifications by viewModel.filteredNotifications.collectAsState()
    val rateLimitInfo by viewModel.rateLimitInfo.collectAsState()

    val isSyncing = syncStatus is SyncStatus.Syncing

    MadoPullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = { viewModel.triggerSync(context) },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OfflineBanner(
                isOffline = isOffline,
                onRetry = { viewModel.triggerSync(context) }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Account Profile Card
                item {
                    AccountCard(
                        authState = authState,
                        monitoredCount = monitoredCount,
                        isSyncing = isSyncing,
                        onSyncClick = { viewModel.triggerSync(context) },
                        rateLimitInfo = rateLimitInfo,
                        isOffline = isOffline,
                        onManageReposClick = onNavigateToRepositories,
                        onSignInClick = { viewModel.disconnect() }
                    )
                }

                // 2. Assistant Highlight Banner (if there are actionable items)
                if (assistantSummary.totalActionableItems > 0) {
                    item {
                        AssistantBannerCard(
                            summary = assistantSummary,
                            onViewAssistantClick = onNavigateToAssistant
                        )
                    }
                }

                // 3. Activity Summary Metrics Grid
                item {
                    ActivityMetricsGrid(
                        unreadCount = unreadCount,
                        pendingReviews = assistantSummary.pendingReviewRequests,
                        assignedIssues = assistantSummary.assignedIssues,
                        failedWorkflows = assistantSummary.failedWorkflows,
                        onNavigateToNotifications = onNavigateToNotifications,
                        onNavigateToAssistant = onNavigateToAssistant
                    )
                }

                // 4. Recent GitHub Events & Alerts Timeline
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Activity Timeline",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "View All",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onNavigateToNotifications() }
                                .padding(4.dp)
                        )
                    }
                }

                if (allNotifications.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Default.CheckCircle,
                            title = "You're all caught up!",
                            description = "No new events or notifications found on your monitored repositories.",
                            actionButtonLabel = "Sync Now",
                            onActionClick = { viewModel.triggerSync(context) }
                        )
                    }
                } else {
                    items(allNotifications.take(10), key = { it.id }) { notification ->
                        TimelineEventCard(
                            notification = notification,
                            onCardClick = {
                                openExternalUrl(
                                    context,
                                    if (notification.targetUrl.isNotBlank()) notification.targetUrl
                                    else "https://github.com/${notification.repoFullName}"
                                )
                            },
                            onMarkReadClick = {
                                viewModel.markNotificationRead(notification.id)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun AccountCard(
    authState: AuthState,
    monitoredCount: Int,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    rateLimitInfo: Pair<Int, Int>,
    isOffline: Boolean,
    onManageReposClick: () -> Unit,
    onSignInClick: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (authState) {
                    is AuthState.Authenticated -> {
                        if (!authState.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = authState.avatarUrl,
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "User Profile",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(50.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = authState.displayName ?: authState.username,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "@${authState.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                                        .clickable { onManageReposClick() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Source, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$monitoredCount repos",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Surface(
                                    color = if (isOffline) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .border(
                                            1.dp,
                                            if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                                            MaterialTheme.shapes.small
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isOffline) {
                                            Icon(
                                                imageVector = Icons.Default.CloudOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Offline",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        } else {
                                            Text(
                                                text = "Active",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SyncButton(
                                isSyncing = isSyncing,
                                onSyncClick = onSyncClick
                            )
                            IconButton(
                                onClick = onManageReposClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Manage Repos",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connect GitHub Account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Authenticate to monitor private and public repos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SyncButton(
                                isSyncing = isSyncing,
                                onSyncClick = onSyncClick
                            )
                            Button(
                                onClick = onSignInClick,
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Connect", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (authState is AuthState.Authenticated && !isOffline && rateLimitInfo.second > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                RateLimitGauge(
                    remaining = rateLimitInfo.first,
                    limit = rateLimitInfo.second
                )
            }
        }

    }
}

@Composable
private fun AssistantBannerCard(
    summary: com.aipos.madogit.data.repository.AssistantSummary,
    onViewAssistantClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onViewAssistantClick() }
            .testTag("assistant_banner_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Assistant AI",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GitHub Assistant Digest",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${summary.totalActionableItems} items require your attention",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${summary.pendingReviewRequests} review requests, ${summary.assignedIssues} issues, ${summary.failedWorkflows} failed runs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "View Assistant",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ActivityMetricsGrid(
    unreadCount: Int,
    pendingReviews: Int,
    assignedIssues: Int,
    failedWorkflows: Int,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAssistant: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "Unread Alerts",
                count = unreadCount,
                icon = Icons.Default.Notifications,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                bgColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToNotifications() }
            )

            MetricCard(
                title = "Review Requests",
                count = pendingReviews,
                icon = Icons.Default.PlayArrow,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                bgColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToAssistant() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "Assigned Issues",
                count = assignedIssues,
                icon = Icons.Default.BugReport,
                iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                bgColor = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToAssistant() }
            )

            MetricCard(
                title = "Failed CI/CD",
                count = failedWorkflows,
                icon = Icons.Default.Error,
                iconColor = MaterialTheme.colorScheme.onErrorContainer,
                bgColor = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToAssistant() }
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    count: Int,
    icon: ImageVector,
    iconColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TimelineEventCard(
    notification: GitHubNotificationEntity,
    onCardClick: () -> Unit,
    onMarkReadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnread = !notification.isRead

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = if (isUnread) 1.5.dp else 1.dp,
            color = if (isUnread) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onCardClick() }
            .testTag("timeline_event_card_${notification.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Unread dot, Avatar, Repo, StatusBadge, Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (!notification.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = notification.avatarUrl,
                            contentDescription = notification.author,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = notification.repoFullName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusBadge(
                        text = notification.eventType.replace('_', ' '),
                        category = notification.category,
                        actionState = notification.actionState
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatRelativeTime(notification.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (notification.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@${notification.author}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isUnread) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .clickable { onMarkReadClick() }
                                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Mark Read",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .clickable { onCardClick() }
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Open",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

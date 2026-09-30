package com.aipos.madogit.ui.notifications

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aipos.madogit.data.database.entities.GitHubNotificationEntity
import com.aipos.madogit.ui.MainViewModel
import com.aipos.madogit.ui.components.EmptyStateView
import com.aipos.madogit.ui.components.StatusBadge
import com.aipos.madogit.ui.components.SyncButton
import com.aipos.madogit.ui.components.formatRelativeTime
import com.aipos.madogit.ui.components.openExternalUrl
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val notifications by viewModel.filteredNotifications.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val searchQuery by viewModel.notificationSearchQuery.collectAsState()
    val unreadOnly by viewModel.unreadOnlyFilter.collectAsState()

    val categories = listOf("ALL", "PR", "ISSUE", "WORKFLOW", "RELEASE", "ACTIVITY")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notification Inbox",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (unreadCount > 0) "$unreadCount unread alert${if (unreadCount > 1) "s" else ""}" else "All caught up",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (unreadCount > 0) {
                    IconButton(
                        onClick = {
                            viewModel.markAllNotificationsRead()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("All notifications marked as read")
                            }
                        },
                        modifier = Modifier.testTag("mark_all_read_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Mark all as read",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = {
                        viewModel.clearAllNotifications()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Notifications cleared")
                        }
                    },
                    modifier = Modifier.testTag("clear_all_notifications_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear all notifications",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SyncButton(
                    isSyncing = syncStatus is com.aipos.madogit.data.repository.SyncStatus.Syncing,
                    onSyncClick = { viewModel.triggerSync(context) }
                )
            }

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setNotificationSearchQuery(it) },
                    placeholder = { Text("Search by title, repository, author...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setNotificationSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }

            // Filter Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "Unread" Toggle Chip
                item {
                    FilterChip(
                        selected = unreadOnly,
                        onClick = { viewModel.toggleUnreadOnlyFilter() },
                        label = {
                            Text(
                                text = "Unread ($unreadCount)",
                                fontSize = 12.sp,
                                fontWeight = if (unreadOnly) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            if (unreadOnly) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            enabled = true,
                            selected = unreadOnly
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.testTag("filter_chip_unread_toggle")
                    )
                }

                // Category Chips
                items(categories) { category ->
                    val isSelected = selectedCategory.equals(category, ignoreCase = true)
                    val label = when (category) {
                        "PR" -> "Pull Requests"
                        "ISSUE" -> "Issues"
                        "WORKFLOW" -> "Actions"
                        "RELEASE" -> "Releases"
                        "ACTIVITY" -> "Activity"
                        else -> "All Events"
                    }

                    val icon: ImageVector? = when (category) {
                        "PR" -> Icons.AutoMirrored.Filled.CallMerge
                        "ISSUE" -> Icons.Default.Adjust
                        "WORKFLOW" -> Icons.Default.PlayCircle
                        "RELEASE" -> Icons.Default.LocalOffer
                        "ACTIVITY" -> Icons.Default.AlternateEmail
                        else -> null
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setNotificationCategory(category) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (icon != null) {
                            {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            enabled = true,
                            selected = isSelected
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.testTag("filter_chip_$category")
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Notifications List / Empty State
            if (notifications.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.NotificationsNone,
                    title = if (searchQuery.isNotEmpty() || unreadOnly) "No matching notifications" else "No notifications in this filter",
                    description = if (searchQuery.isNotEmpty()) "Try adjusting your search query or clearing the active filters."
                    else "Trigger a sync or monitor more repositories to receive alerts.",
                    actionButtonLabel = if (searchQuery.isNotEmpty() || unreadOnly) "Clear Filters" else "Sync Now",
                    onActionClick = {
                        if (searchQuery.isNotEmpty() || unreadOnly) {
                            viewModel.setNotificationSearchQuery("")
                            viewModel.setUnreadOnlyFilter(false)
                        } else {
                            viewModel.triggerSync(context)
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        NotificationHistoryItem(
                            notification = item,
                            onOpen = {
                                openExternalUrl(
                                    context,
                                    if (item.targetUrl.isNotBlank()) item.targetUrl
                                    else "https://github.com/${item.repoFullName}"
                                )
                            },
                            onMarkRead = {
                                viewModel.markNotificationRead(item.id)
                            },
                            onDelete = {
                                viewModel.deleteNotification(item.id)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Notification dismissed")
                                }
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun NotificationHistoryItem(
    notification: GitHubNotificationEntity,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit
) {
    val isUnread = !notification.isRead

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onOpen() }
            .border(
                width = if (isUnread) 1.5.dp else 1.dp,
                color = if (isUnread) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium
            )
            .testTag("notification_item_${notification.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Avatar, Repo, StatusBadge, Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Pulsing / glowing unread accent indicator
                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Author Avatar or Icon
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

            // Notification Title
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium
            )

            // Notification Body
            if (notification.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Actions Row: Author and Buttons
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
                    // Mark as Read Button
                    if (isUnread) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .clickable { onMarkRead() }
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

                    // Open in GitHub Button
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .clickable { onOpen() }
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

                    // Dismiss / Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete notification",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

package com.aipos.madogit.ui.assistant

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.aipos.madogit.data.database.entities.GitHubNotificationEntity
import com.aipos.madogit.ui.MainViewModel
import com.aipos.madogit.ui.components.EmptyStateView
import com.aipos.madogit.ui.components.StatusBadge
import com.aipos.madogit.ui.components.SyncButton
import com.aipos.madogit.ui.components.formatRelativeTime
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.aipos.madogit.ui.components.MadoPullToRefreshBox
import com.aipos.madogit.ui.components.openExternalUrl

@Composable
fun AssistantScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val summary by viewModel.assistantSummary.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    var selectedTriageTab by rememberSaveable { mutableIntStateOf(0) }

    val filteredItems = remember(summary.topActionableItems, selectedTriageTab) {
        when (selectedTriageTab) {
            1 -> summary.topActionableItems.filter { it.category.equals("PR", ignoreCase = true) || it.actionState == "REVIEW_REQUESTED" }
            2 -> summary.topActionableItems.filter { it.category.equals("ISSUE", ignoreCase = true) || it.actionState == "ASSIGNED" }
            3 -> summary.topActionableItems.filter { it.category.equals("WORKFLOW", ignoreCase = true) || it.actionState?.uppercase() in listOf("FAILED", "FAILURE", "ERROR") }
            else -> summary.topActionableItems
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GitHub Assistant",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Smart triage & pending action items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SyncButton(
                isSyncing = syncStatus is com.aipos.madogit.data.repository.SyncStatus.Syncing,
                onSyncClick = { viewModel.triggerSync() }
            )
        }

        MadoPullToRefreshBox(
            isRefreshing = syncStatus is com.aipos.madogit.data.repository.SyncStatus.Syncing,
            onRefresh = { viewModel.triggerSync() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Attention Banner
                item {
                    val hasItems = summary.totalActionableItems > 0
                    val containerColor = if (hasItems) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                    val contentColor = if (hasItems) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    val borderColor = if (hasItems) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)

                    Surface(
                        color = containerColor,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("assistant_summary_card")
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = if (hasItems)
                                    "You have ${summary.totalActionableItems} items that may need your attention."
                                else
                                    "Inbox Zero! 0 urgent items requiring developer attention.",
                                style = MaterialTheme.typography.titleMedium,
                                color = contentColor,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Factual breakdown lines
                            AssistantBreakdownRow(
                                icon = Icons.Default.PlayArrow,
                                text = "${summary.pendingReviewRequests} pull requests waiting for your review",
                                iconColor = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            AssistantBreakdownRow(
                                icon = Icons.Default.BugReport,
                                text = "${summary.assignedIssues} issues assigned to you",
                                iconColor = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            AssistantBreakdownRow(
                                icon = Icons.Default.ErrorOutline,
                                text = "${summary.failedWorkflows} failed GitHub Actions workflows",
                                iconColor = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Triage Segmented Filter Chips
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf(
                            Pair("All (${summary.totalActionableItems})", 0),
                            Pair("Reviews (${summary.pendingReviewRequests})", 1),
                            Pair("Issues (${summary.assignedIssues})", 2),
                            Pair("CI Runs (${summary.failedWorkflows})", 3)
                        )

                        items(filters.size) { index ->
                            val (title, tabIndex) = filters[index]
                            val isSelected = selectedTriageTab == tabIndex
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTriageTab = tabIndex },
                                label = {
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = MaterialTheme.shapes.small
                            )
                        }
                    }
                }

                if (filteredItems.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Default.CheckCircle,
                            title = if (summary.totalActionableItems == 0) "Inbox Zero Achieved!" else "No Items In This Filter",
                            description = if (summary.totalActionableItems == 0)
                                "Everything on your monitored repositories is up-to-date, reviewed, and passing CI."
                            else "No items found under this triage category.",
                            actionButtonLabel = "Sync Latest",
                            onActionClick = { viewModel.triggerSync() }
                        )
                    }
                } else {
                    items(filteredItems, key = { it.id }) { item ->
                        ActionableTaskCard(
                            item = item,
                            onOpenUrl = {
                                openExternalUrl(
                                    context,
                                    if (item.targetUrl.isNotBlank()) item.targetUrl
                                    else "https://github.com/${item.repoFullName}"
                                )
                            },
                            onMarkDone = {
                                viewModel.markNotificationRead(item.id)
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
private fun AssistantBreakdownRow(
    icon: ImageVector,
    text: String,
    iconColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ActionableTaskCard(
    item: GitHubNotificationEntity,
    onOpenUrl: () -> Unit,
    onMarkDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("actionable_task_card_${item.id}")
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
                    if (!item.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.avatarUrl,
                            contentDescription = item.author,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = item.repoFullName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusBadge(
                        text = item.eventType.replace('_', ' '),
                        category = item.category,
                        actionState = item.actionState
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatRelativeTime(item.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (item.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@${item.author}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { onMarkDone() }
                    ) {
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Button(
                        onClick = onOpenUrl,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Action on GitHub", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

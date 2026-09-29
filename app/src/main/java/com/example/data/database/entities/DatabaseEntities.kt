package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "monitored_repos",
    indices = [
        Index("isMonitored"),
        Index("lastSyncedAt")
    ]
)
data class MonitoredRepoEntity(
    @PrimaryKey val id: Long,
    val fullName: String,
    val name: String,
    val owner: String,
    val isPrivate: Boolean,
    val description: String?,
    val stargazersCount: Int,
    val forksCount: Int = 0,
    val defaultBranch: String,
    val htmlUrl: String,
    val isMonitored: Boolean = false,
    val lastSyncedAt: Long = 0L
)

@Entity(
    tableName = "notifications",
    indices = [
        Index("timestamp"),
        Index("category"),
        Index("isRead")
    ]
)
data class GitHubNotificationEntity(
    @PrimaryKey val id: String,
    val eventType: String,
    val category: String,
    val repoFullName: String,
    val title: String,
    val body: String,
    val author: String,
    val avatarUrl: String?,
    val targetUrl: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isNotified: Boolean = false,
    val actionState: String? = null
)

@Entity(
    tableName = "processed_events",
    indices = [
        Index("repoFullName")
    ]
)
data class ProcessedEventEntity(
    @PrimaryKey val eventId: String,
    val eventType: String,
    val repoFullName: String,
    val processedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_logs",
    indices = [
        Index("timestamp")
    ]
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String,
    val itemsFound: Int,
    val newNotificationsCount: Int,
    val errorMessage: String? = null
)


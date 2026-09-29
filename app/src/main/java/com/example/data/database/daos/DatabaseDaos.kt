package com.example.data.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.database.entities.GitHubNotificationEntity
import com.example.data.database.entities.MonitoredRepoEntity
import com.example.data.database.entities.ProcessedEventEntity
import com.example.data.database.entities.SyncLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {
    @Query("SELECT * FROM monitored_repos ORDER BY isMonitored DESC, stargazersCount DESC, name ASC")
    fun getAllReposFlow(): Flow<List<MonitoredRepoEntity>>

    @Query("SELECT * FROM monitored_repos WHERE isMonitored = 1 ORDER BY name ASC")
    fun getMonitoredReposFlow(): Flow<List<MonitoredRepoEntity>>

    @Query("SELECT * FROM monitored_repos WHERE isMonitored = 1")
    suspend fun getMonitoredReposSync(): List<MonitoredRepoEntity>

    @Query("SELECT * FROM monitored_repos WHERE isMonitored = 1 ORDER BY lastSyncedAt ASC LIMIT :limit")
    suspend fun getMonitoredReposToSync(limit: Int = 5): List<MonitoredRepoEntity>

    @Query("SELECT * FROM monitored_repos WHERE id = :id")
    suspend fun getRepoById(id: Long): MonitoredRepoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(repos: List<MonitoredRepoEntity>): List<Long>

    @Update
    suspend fun updateRepos(repos: List<MonitoredRepoEntity>)

    @Query("UPDATE monitored_repos SET isMonitored = :isMonitored WHERE id = :repoId")
    suspend fun setMonitored(repoId: Long, isMonitored: Boolean)

    @Query("UPDATE monitored_repos SET isMonitored = :isMonitored")
    suspend fun setAllMonitored(isMonitored: Boolean)

    @Query("UPDATE monitored_repos SET lastSyncedAt = :timestamp WHERE id = :repoId")
    suspend fun updateLastSynced(repoId: Long, timestamp: Long)

    @Query("SELECT COUNT(*) FROM monitored_repos")
    suspend fun getRepoCount(): Int

    @Query("SELECT COUNT(*) FROM monitored_repos WHERE isMonitored = 1")
    fun getMonitoredCountFlow(): Flow<Int>
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<GitHubNotificationEntity>>

    @Query("SELECT * FROM notifications WHERE category = :category ORDER BY timestamp DESC")
    fun getNotificationsByCategoryFlow(category: String): Flow<List<GitHubNotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Query("SELECT * FROM notifications WHERE isRead = 0 ORDER BY timestamp DESC")
    suspend fun getUnreadNotifications(): List<GitHubNotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: GitHubNotificationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(notifications: List<GitHubNotificationEntity>): List<Long>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAllNotifications()

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getTotalNotificationCount(): Int
}

@Dao
interface ProcessedEventDao {
    @Query("SELECT COUNT(*) > 0 FROM processed_events WHERE eventId = :eventId")
    suspend fun isEventProcessed(eventId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProcessedEvent(event: ProcessedEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProcessedEvents(events: List<ProcessedEventEntity>)

    @Query("SELECT COUNT(*) FROM processed_events")
    suspend fun getProcessedCount(): Int

    @Query("DELETE FROM processed_events")
    suspend fun clearProcessedEvents()
}

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insertLog(log: SyncLogEntity)

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 10): Flow<List<SyncLogEntity>>

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLog(): SyncLogEntity?

    @Query("DELETE FROM sync_logs")
    suspend fun clearLogs()
}

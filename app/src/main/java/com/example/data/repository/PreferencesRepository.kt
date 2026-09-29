package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationPreferences(
    // Pull Requests
    val prMaster: Boolean = true,
    val prOpened: Boolean = true,
    val prReviewRequested: Boolean = true,
    val prApproved: Boolean = true,
    val prChangesRequested: Boolean = true,
    val prMerged: Boolean = true,
    val prClosed: Boolean = true,

    // Issues
    val issueMaster: Boolean = true,
    val issueOpened: Boolean = true,
    val issueAssigned: Boolean = true,
    val issueMentioned: Boolean = true,
    val issueCommented: Boolean = true,

    // Actions (CI/CD)
    val actionMaster: Boolean = true,
    val actionFailed: Boolean = true,
    val actionSucceeded: Boolean = false,
    val actionCancelled: Boolean = false,

    // Releases
    val releaseMaster: Boolean = true,
    val releasePublished: Boolean = true,

    // Activity
    val activityMaster: Boolean = true,
    val activityCommits: Boolean = true,
    val activityComments: Boolean = true,

    // Sound & Vibration & Grouping
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val groupingEnabled: Boolean = true
)

enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

data class SyncPreferences(
    val intervalMinutes: Long = 15L, // 15, 30, 60, 120, 360, or 0 (manual)
    val wifiOnly: Boolean = false,
    val monitorAllByDefault: Boolean = false
)

class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gh_notifier_prefs", Context.MODE_PRIVATE)

    private val _notificationPrefs = MutableStateFlow(loadNotificationPreferences())
    val notificationPrefs: StateFlow<NotificationPreferences> = _notificationPrefs.asStateFlow()

    private val _syncPrefs = MutableStateFlow(loadSyncPreferences())
    val syncPrefs: StateFlow<SyncPreferences> = _syncPrefs.asStateFlow()

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean("pref_dynamic_color", true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _isFirstLaunch = MutableStateFlow(prefs.getBoolean(KEY_FIRST_LAUNCH, true))
    val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC_TIME, 0L))
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _rateLimitInfo = MutableStateFlow(loadRateLimitInfo())
    val rateLimitInfo: StateFlow<Pair<Int, Int>> = _rateLimitInfo.asStateFlow() // Pair(remaining, limit)

    private fun loadNotificationPreferences(): NotificationPreferences {
        return NotificationPreferences(
            prMaster = prefs.getBoolean("pref_pr_master", true),
            prOpened = prefs.getBoolean("pref_pr_opened", true),
            prReviewRequested = prefs.getBoolean("pref_pr_review_requested", true),
            prApproved = prefs.getBoolean("pref_pr_approved", true),
            prChangesRequested = prefs.getBoolean("pref_pr_changes_requested", true),
            prMerged = prefs.getBoolean("pref_pr_merged", true),
            prClosed = prefs.getBoolean("pref_pr_closed", true),

            issueMaster = prefs.getBoolean("pref_issue_master", true),
            issueOpened = prefs.getBoolean("pref_issue_opened", true),
            issueAssigned = prefs.getBoolean("pref_issue_assigned", true),
            issueMentioned = prefs.getBoolean("pref_issue_mentioned", true),
            issueCommented = prefs.getBoolean("pref_issue_commented", true),

            actionMaster = prefs.getBoolean("pref_action_master", true),
            actionFailed = prefs.getBoolean("pref_action_failed", true),
            actionSucceeded = prefs.getBoolean("pref_action_succeeded", false),
            actionCancelled = prefs.getBoolean("pref_action_cancelled", false),

            releaseMaster = prefs.getBoolean("pref_release_master", true),
            releasePublished = prefs.getBoolean("pref_release_published", true),

            activityMaster = prefs.getBoolean("pref_activity_master", true),
            activityCommits = prefs.getBoolean("pref_activity_commits", true),
            activityComments = prefs.getBoolean("pref_activity_comments", true),

            soundEnabled = prefs.getBoolean("pref_sound_enabled", true),
            vibrationEnabled = prefs.getBoolean("pref_vibration_enabled", true),
            groupingEnabled = prefs.getBoolean("pref_grouping_enabled", true)
        )
    }

    private fun loadSyncPreferences(): SyncPreferences {
        return SyncPreferences(
            intervalMinutes = prefs.getLong("pref_sync_interval", 15L),
            wifiOnly = prefs.getBoolean("pref_wifi_only", false),
            monitorAllByDefault = prefs.getBoolean("pref_monitor_all", false)
        )
    }

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString("pref_theme_mode", ThemeMode.DARK.name)
        return try {
            ThemeMode.valueOf(name ?: ThemeMode.DARK.name)
        } catch (_: Exception) {
            ThemeMode.DARK
        }
    }

    private fun loadRateLimitInfo(): Pair<Int, Int> {
        val remaining = prefs.getInt("rate_limit_remaining", 5000)
        val limit = prefs.getInt("rate_limit_limit", 5000)
        return Pair(remaining, limit)
    }

    fun updateNotificationPreferences(prefsUpdate: NotificationPreferences) {
        _notificationPrefs.value = prefsUpdate
        prefs.edit {
            putBoolean("pref_pr_master", prefsUpdate.prMaster)
            putBoolean("pref_pr_opened", prefsUpdate.prOpened)
            putBoolean("pref_pr_review_requested", prefsUpdate.prReviewRequested)
            putBoolean("pref_pr_approved", prefsUpdate.prApproved)
            putBoolean("pref_pr_changes_requested", prefsUpdate.prChangesRequested)
            putBoolean("pref_pr_merged", prefsUpdate.prMerged)
            putBoolean("pref_pr_closed", prefsUpdate.prClosed)
            putBoolean("pref_issue_master", prefsUpdate.issueMaster)
            putBoolean("pref_issue_opened", prefsUpdate.issueOpened)
            putBoolean("pref_issue_assigned", prefsUpdate.issueAssigned)
            putBoolean("pref_issue_mentioned", prefsUpdate.issueMentioned)
            putBoolean("pref_issue_commented", prefsUpdate.issueCommented)
            putBoolean("pref_action_master", prefsUpdate.actionMaster)
            putBoolean("pref_action_failed", prefsUpdate.actionFailed)
            putBoolean("pref_action_succeeded", prefsUpdate.actionSucceeded)
            putBoolean("pref_action_cancelled", prefsUpdate.actionCancelled)
            putBoolean("pref_release_master", prefsUpdate.releaseMaster)
            putBoolean("pref_release_published", prefsUpdate.releasePublished)
            putBoolean("pref_activity_master", prefsUpdate.activityMaster)
            putBoolean("pref_activity_commits", prefsUpdate.activityCommits)
            putBoolean("pref_activity_comments", prefsUpdate.activityComments)
            putBoolean("pref_sound_enabled", prefsUpdate.soundEnabled)
            putBoolean("pref_vibration_enabled", prefsUpdate.vibrationEnabled)
            putBoolean("pref_grouping_enabled", prefsUpdate.groupingEnabled)
        }
    }

    fun updateSyncPreferences(syncUpdate: SyncPreferences) {
        _syncPrefs.value = syncUpdate
        prefs.edit {
            putLong("pref_sync_interval", syncUpdate.intervalMinutes)
            putBoolean("pref_wifi_only", syncUpdate.wifiOnly)
            putBoolean("pref_monitor_all", syncUpdate.monitorAllByDefault)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit { putString("pref_theme_mode", mode.name) }
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit { putBoolean("pref_dynamic_color", enabled) }
    }

    fun setFirstLaunchCompleted() {
        _isFirstLaunch.value = false
        prefs.edit { putBoolean(KEY_FIRST_LAUNCH, false) }
    }

    fun updateLastSyncTime(timestamp: Long = System.currentTimeMillis()) {
        _lastSyncTimestamp.value = timestamp
        prefs.edit { putLong(KEY_LAST_SYNC_TIME, timestamp) }
    }

    fun updateRateLimit(remaining: Int, limit: Int) {
        _rateLimitInfo.value = Pair(remaining, limit)
        prefs.edit {
            putInt("rate_limit_remaining", remaining)
            putInt("rate_limit_limit", limit)
        }
    }

    companion object {
        private const val KEY_FIRST_LAUNCH = "first_launch_done"
        private const val KEY_LAST_SYNC_TIME = "last_sync_timestamp"
    }
}

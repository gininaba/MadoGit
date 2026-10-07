package com.aipos.madogit.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aipos.madogit.data.database.daos.NotificationDao
import com.aipos.madogit.data.database.daos.ProcessedEventDao
import com.aipos.madogit.data.database.daos.RepoDao
import com.aipos.madogit.data.database.daos.SyncLogDao
import com.aipos.madogit.data.database.entities.GitHubNotificationEntity
import com.aipos.madogit.data.database.entities.MonitoredRepoEntity
import com.aipos.madogit.data.database.entities.ProcessedEventEntity
import com.aipos.madogit.data.database.entities.SyncLogEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MonitoredRepoEntity::class,
        GitHubNotificationEntity::class,
        ProcessedEventEntity::class,
        SyncLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
    abstract fun notificationDao(): NotificationDao
    abstract fun processedEventDao(): ProcessedEventDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE monitored_repos ADD COLUMN language TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "github_notifier.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

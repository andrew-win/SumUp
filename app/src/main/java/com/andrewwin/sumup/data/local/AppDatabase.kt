package com.andrewwin.sumup.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.dao.SummaryDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.data.local.entities.UserPreferences

@Database(
    entities = [
        SourceGroup::class, 
        Source::class, 
        Article::class, 
        AiModelConfig::class, 
        Summary::class,
        UserPreferences::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun articleDao(): ArticleDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun summaryDao(): SummaryDao
    abstract fun userPreferencesDao(): UserPreferencesDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sumup_database"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL(
                                "INSERT OR IGNORE INTO source_groups (id, name, isEnabled, isDeletable) VALUES (1, 'Без категорії', 1, 0)"
                            )
                            db.execSQL(
                                "INSERT OR IGNORE INTO user_preferences (id, aiStrategy, isScheduledSummaryEnabled, scheduledHour, scheduledMinute, lastWorkRunTimestamp, isDeduplicationEnabled, deduplicationThreshold, minMentions, isImportanceFilterEnabled) " +
                                "VALUES (0, 'ADAPTIVE', 0, 8, 0, 0, 0, 0.6, 2, 1)"
                            )
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

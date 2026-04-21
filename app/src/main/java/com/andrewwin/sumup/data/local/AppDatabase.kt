package com.andrewwin.sumup.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.andrewwin.sumup.data.local.dao.AiModelDao
import com.andrewwin.sumup.data.local.dao.ArticleDao
import com.andrewwin.sumup.data.local.dao.ArticleSimilarityDao
import com.andrewwin.sumup.data.local.dao.SourceDao
import com.andrewwin.sumup.data.local.dao.SummaryDao
import com.andrewwin.sumup.data.local.dao.UserPreferencesDao
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.Article
import com.andrewwin.sumup.data.local.entities.ArticleSimilarity
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.Summary
import com.andrewwin.sumup.data.local.entities.UserPreferences

@Database(
    entities = [
        SourceGroup::class, 
        Source::class, 
        Article::class, 
        ArticleSimilarity::class,
        AiModelConfig::class, 
        Summary::class,
        UserPreferences::class
    ],
    version = 41,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun articleDao(): ArticleDao
    abstract fun articleSimilarityDao(): ArticleSimilarityDao
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
                    .addMigrations(
                        MIGRATION_31_32,
                        MIGRATION_32_33,
                        MIGRATION_33_34,
                        MIGRATION_34_35,
                        MIGRATION_35_36,
                        MIGRATION_36_37,
                        MIGRATION_37_38,
                        MIGRATION_38_39,
                        MIGRATION_39_40,
                        MIGRATION_40_41
                    )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            val defaultPrompt = context.getString(com.andrewwin.sumup.R.string.summary_prompt_default)
                                .replace("'", "''")
                            db.execSQL(
                                "INSERT OR IGNORE INTO source_groups (id, name, isEnabled, isDeletable) VALUES (1, 'Без категорії', 1, 0)"
                            )
                            db.execSQL(
                                "INSERT OR IGNORE INTO user_preferences (id, aiStrategy, isScheduledSummaryEnabled, isScheduledSummaryPushEnabled, scheduledHour, scheduledMinute, lastWorkRunTimestamp, isDeduplicationEnabled, deduplicationStrategy, localDeduplicationThreshold, cloudDeduplicationThreshold, minMentions, isImportanceFilterEnabled, isAdaptiveExtractivePreprocessingEnabled, adaptiveExtractiveOnlyBelowChars, adaptiveExtractiveCompressAboveChars, adaptiveExtractiveCompressionPercent, summaryItemsPerNewsInFeed, summaryItemsPerNewsInScheduled, summaryNewsInFeedExtractive, summaryNewsInFeedCloud, summaryNewsInScheduledExtractive, summaryNewsInScheduledCloud, extractiveSentencesInFeed, extractiveNewsInFeed, extractiveSentencesInScheduled, extractiveNewsInScheduled, showLastSummariesCount, showInfographicNewsCount, isHideSingleNewsEnabled, aiMaxCharsPerArticle, aiMaxCharsPerFeedArticle, aiMaxCharsTotal, summaryPrompt, isCustomSummaryPromptEnabled, isFeedMediaEnabled, isFeedDescriptionEnabled, isFeedSummaryUseFullTextEnabled, isRecommendationsEnabled, articleAutoCleanupDays, appThemeMode, appLanguage, summaryLanguage) " +
                                "VALUES (0, 'ADAPTIVE', 0, 0, 8, 0, 0, 0, 'CLOUD', 0.55, 0.75, 2, 1, 1, 500, 500, 30, 3, 3, 4, 4, 4, 4, 3, 4, 3, 4, 5, 4, 0, 1000, 1000, 12000, '$defaultPrompt', 0, 1, 0, 0, 1, 3, 'SYSTEM', 'UK', 'UK')"
                            )
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN summaryLanguage TEXT NOT NULL DEFAULT 'ORIGINAL'"
                )
            }
        }

        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN deduplicationStrategy TEXT NOT NULL DEFAULT 'ADAPTIVE'"
                )
            }
        }

        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN isFeedSummaryUseFullTextEnabled INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE summaries ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN articleAutoCleanupDays INTEGER NOT NULL DEFAULT 3"
                )
            }
        }

        private val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM sources WHERE type = 'WEBSITE'")
            }
        }

        private val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE user_preferences
                    SET summaryLanguage = CASE appLanguage
                        WHEN 'EN' THEN 'EN'
                        ELSE 'UK'
                    END
                    WHERE summaryLanguage = 'ORIGINAL' OR summaryLanguage IS NULL
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE ai_model_configs ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM'"
                )
            }
        }

        private val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE ai_model_configs ADD COLUMN isUseNow INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE user_preferences
                    SET deduplicationStrategy = 'CLOUD'
                    WHERE deduplicationStrategy = 'ADAPTIVE' OR deduplicationStrategy IS NULL
                    """.trimIndent()
                )
            }
        }
    }
}







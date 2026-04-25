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
import com.andrewwin.sumup.data.local.dao.SavedArticleDao
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
import com.andrewwin.sumup.data.local.entities.SavedArticle

@Database(
    entities = [
        SourceGroup::class, 
        Source::class, 
        Article::class, 
        ArticleSimilarity::class,
        SavedArticle::class,
        AiModelConfig::class, 
        Summary::class,
        UserPreferences::class
    ],
    version = 46,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun articleDao(): ArticleDao
    abstract fun articleSimilarityDao(): ArticleSimilarityDao
    abstract fun savedArticleDao(): SavedArticleDao
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
                        MIGRATION_40_41,
                        MIGRATION_41_42,
                        MIGRATION_42_43,
                        MIGRATION_43_44,
                        MIGRATION_44_45,
                        MIGRATION_45_46
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
                                "INSERT OR IGNORE INTO user_preferences (id, aiStrategy, isScheduledSummaryEnabled, isScheduledSummaryPushEnabled, scheduledHour, scheduledMinute, lastWorkRunTimestamp, isDeduplicationEnabled, deduplicationStrategy, localDeduplicationThreshold, cloudDeduplicationThreshold, minMentions, isImportanceFilterEnabled, isAdaptiveExtractivePreprocessingEnabled, adaptiveExtractiveOnlyBelowChars, adaptiveExtractiveHighCompressionAboveChars, adaptiveExtractiveCompressionPercentMedium, adaptiveExtractiveCompressionPercentHigh, summaryItemsPerNewsInFeed, summaryItemsPerNewsInScheduled, summaryNewsInFeedExtractive, summaryNewsInFeedCloud, summaryNewsInScheduledExtractive, summaryNewsInScheduledCloud, extractiveNewsInFeed, extractiveSentencesInScheduled, extractiveNewsInScheduled, showLastSummariesCount, showInfographicNewsCount, isHideSingleNewsEnabled, aiMaxCharsPerArticle, aiMaxCharsPerFeedArticle, aiMaxCharsTotal, summaryPrompt, isCustomSummaryPromptEnabled, isFeedMediaEnabled, isFeedDescriptionEnabled, isFeedSummaryUseFullTextEnabled, isRecommendationsEnabled, articleAutoCleanupDays, appThemeMode, appLanguage, summaryLanguage) " +
                                "VALUES (0, 'ADAPTIVE', 0, 0, 8, 0, 0, 0, 'CLOUD', 0.55, 0.75, 2, 1, 1, 1000, 3000, 50, 25, 3, 3, 4, 4, 4, 4, 4, 3, 4, 5, 4, 0, 1000, 1000, 12000, '$defaultPrompt', 0, 1, 0, 0, 1, 3, 'SYSTEM', 'UK', 'UK')"
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

        private val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS saved_articles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        url TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        mediaUrl TEXT,
                        videoId TEXT,
                        publishedAt INTEGER NOT NULL,
                        viewCount INTEGER NOT NULL,
                        sourceName TEXT,
                        groupName TEXT,
                        savedAt INTEGER NOT NULL,
                        clusterKey TEXT,
                        clusterScore REAL NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_saved_articles_url ON saved_articles(url)"
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO saved_articles (
                        url, title, content, mediaUrl, videoId, publishedAt, viewCount, sourceName, groupName, savedAt, clusterKey, clusterScore
                    )
                    SELECT
                        a.url,
                        CASE
                            WHEN TRIM(COALESCE(a.title, '')) != '' THEN a.title
                            ELSE COALESCE(s.name, a.url)
                        END AS title,
                        COALESCE(a.content, a.url) AS content,
                        a.mediaUrl,
                        a.videoId,
                        a.publishedAt,
                        a.viewCount,
                        s.name AS sourceName,
                        sg.name AS groupName,
                        CAST(strftime('%s','now') AS INTEGER) * 1000 AS savedAt,
                        NULL AS clusterKey,
                        0 AS clusterScore
                    FROM articles a
                    LEFT JOIN sources s ON s.id = a.sourceId
                    LEFT JOIN source_groups sg ON sg.id = s.groupId
                    WHERE a.isFavorite = 1
                    """.trimIndent()
                )
                db.execSQL("UPDATE articles SET isFavorite = 0 WHERE isFavorite = 1")
                db.execSQL("DELETE FROM sources WHERE url = 'sumup://saved-fallback'")
                db.execSQL("DELETE FROM source_groups WHERE name = 'Saved Items'")
                db.execSQL("DELETE FROM source_groups WHERE name = '__internal_saved_group'")
            }
        }

        private val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE saved_articles ADD COLUMN clusterScore REAL NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN adaptiveExtractiveHighCompressionAboveChars INTEGER NOT NULL DEFAULT 3000"
                )
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN adaptiveExtractiveCompressionPercentMedium INTEGER NOT NULL DEFAULT 50"
                )
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN adaptiveExtractiveCompressionPercentHigh INTEGER NOT NULL DEFAULT 25"
                )
                db.execSQL(
                    """
                    UPDATE user_preferences
                    SET adaptiveExtractiveOnlyBelowChars = 1000,
                        adaptiveExtractiveHighCompressionAboveChars = 3000,
                        adaptiveExtractiveCompressionPercentMedium = 50,
                        adaptiveExtractiveCompressionPercentHigh = 25
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE user_preferences
                    SET extractiveSentencesInFeed = 5
                    WHERE id = 0 AND extractiveSentencesInFeed = 3
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_preferences_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        aiStrategy TEXT NOT NULL,
                        isScheduledSummaryEnabled INTEGER NOT NULL,
                        isScheduledSummaryPushEnabled INTEGER NOT NULL,
                        scheduledHour INTEGER NOT NULL,
                        scheduledMinute INTEGER NOT NULL,
                        lastWorkRunTimestamp INTEGER NOT NULL,
                        isDeduplicationEnabled INTEGER NOT NULL,
                        deduplicationStrategy TEXT NOT NULL,
                        localDeduplicationThreshold REAL NOT NULL,
                        cloudDeduplicationThreshold REAL NOT NULL,
                        minMentions INTEGER NOT NULL,
                        isHideSingleNewsEnabled INTEGER NOT NULL,
                        modelPath TEXT,
                        isImportanceFilterEnabled INTEGER NOT NULL,
                        isAdaptiveExtractivePreprocessingEnabled INTEGER NOT NULL,
                        adaptiveExtractiveOnlyBelowChars INTEGER NOT NULL,
                        adaptiveExtractiveHighCompressionAboveChars INTEGER NOT NULL,
                        adaptiveExtractiveCompressionPercentMedium INTEGER NOT NULL,
                        adaptiveExtractiveCompressionPercentHigh INTEGER NOT NULL,
                        summaryItemsPerNewsInFeed INTEGER NOT NULL,
                        summaryItemsPerNewsInScheduled INTEGER NOT NULL,
                        summaryNewsInFeedExtractive INTEGER NOT NULL,
                        summaryNewsInFeedCloud INTEGER NOT NULL,
                        summaryNewsInScheduledExtractive INTEGER NOT NULL,
                        summaryNewsInScheduledCloud INTEGER NOT NULL,
                        extractiveNewsInFeed INTEGER NOT NULL,
                        extractiveSentencesInScheduled INTEGER NOT NULL,
                        extractiveNewsInScheduled INTEGER NOT NULL,
                        showLastSummariesCount INTEGER NOT NULL,
                        showInfographicNewsCount INTEGER NOT NULL,
                        aiMaxCharsPerArticle INTEGER NOT NULL,
                        aiMaxCharsPerFeedArticle INTEGER NOT NULL,
                        aiMaxCharsTotal INTEGER NOT NULL,
                        summaryPrompt TEXT NOT NULL,
                        isCustomSummaryPromptEnabled INTEGER NOT NULL,
                        isFeedMediaEnabled INTEGER NOT NULL,
                        isFeedDescriptionEnabled INTEGER NOT NULL,
                        isFeedSummaryUseFullTextEnabled INTEGER NOT NULL,
                        isRecommendationsEnabled INTEGER NOT NULL,
                        articleAutoCleanupDays INTEGER NOT NULL,
                        appThemeMode TEXT NOT NULL,
                        appLanguage TEXT NOT NULL,
                        summaryLanguage TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO user_preferences_new (
                        id, aiStrategy, isScheduledSummaryEnabled, isScheduledSummaryPushEnabled,
                        scheduledHour, scheduledMinute, lastWorkRunTimestamp, isDeduplicationEnabled,
                        deduplicationStrategy, localDeduplicationThreshold, cloudDeduplicationThreshold,
                        minMentions, isHideSingleNewsEnabled, modelPath, isImportanceFilterEnabled,
                        isAdaptiveExtractivePreprocessingEnabled, adaptiveExtractiveOnlyBelowChars,
                        adaptiveExtractiveHighCompressionAboveChars, adaptiveExtractiveCompressionPercentMedium,
                        adaptiveExtractiveCompressionPercentHigh, summaryItemsPerNewsInFeed,
                        summaryItemsPerNewsInScheduled, summaryNewsInFeedExtractive, summaryNewsInFeedCloud,
                        summaryNewsInScheduledExtractive, summaryNewsInScheduledCloud, extractiveNewsInFeed,
                        extractiveSentencesInScheduled, extractiveNewsInScheduled, showLastSummariesCount,
                        showInfographicNewsCount, aiMaxCharsPerArticle, aiMaxCharsPerFeedArticle,
                        aiMaxCharsTotal, summaryPrompt, isCustomSummaryPromptEnabled, isFeedMediaEnabled,
                        isFeedDescriptionEnabled, isFeedSummaryUseFullTextEnabled, isRecommendationsEnabled,
                        articleAutoCleanupDays, appThemeMode, appLanguage, summaryLanguage
                    )
                    SELECT
                        id, aiStrategy, isScheduledSummaryEnabled, isScheduledSummaryPushEnabled,
                        scheduledHour, scheduledMinute, lastWorkRunTimestamp, isDeduplicationEnabled,
                        deduplicationStrategy, localDeduplicationThreshold, cloudDeduplicationThreshold,
                        minMentions, isHideSingleNewsEnabled, modelPath, isImportanceFilterEnabled,
                        isAdaptiveExtractivePreprocessingEnabled, adaptiveExtractiveOnlyBelowChars,
                        adaptiveExtractiveHighCompressionAboveChars, adaptiveExtractiveCompressionPercentMedium,
                        adaptiveExtractiveCompressionPercentHigh, summaryItemsPerNewsInFeed,
                        summaryItemsPerNewsInScheduled, summaryNewsInFeedExtractive, summaryNewsInFeedCloud,
                        summaryNewsInScheduledExtractive, summaryNewsInScheduledCloud, extractiveNewsInFeed,
                        extractiveSentencesInScheduled, extractiveNewsInScheduled, showLastSummariesCount,
                        showInfographicNewsCount, aiMaxCharsPerArticle, aiMaxCharsPerFeedArticle,
                        aiMaxCharsTotal, summaryPrompt, isCustomSummaryPromptEnabled, isFeedMediaEnabled,
                        isFeedDescriptionEnabled, isFeedSummaryUseFullTextEnabled, isRecommendationsEnabled,
                        articleAutoCleanupDays, appThemeMode, appLanguage, summaryLanguage
                    FROM user_preferences
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE user_preferences")
                db.execSQL("ALTER TABLE user_preferences_new RENAME TO user_preferences")
            }
        }
    }
}







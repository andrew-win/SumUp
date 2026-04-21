package com.andrewwin.sumup.worker

object WorkerContracts {
    const val CLOUD_COLLECTION = "user_sync_backups"
    const val SYNC_PREFS = "sync_prefs"
    const val SUBSCRIPTIONS_PREFS = "suggested_themes_prefs"

    const val KEY_SYNC_ENABLED = "sync_enabled"
    const val KEY_LAST_SYNC_AT = "last_sync_at"
    const val KEY_SYNC_INCLUDE_SOURCES = "sync_include_sources"
    const val KEY_SYNC_INCLUDE_SUBSCRIPTIONS = "sync_include_subscriptions"
    const val KEY_SYNC_INCLUDE_SETTINGS_NO_API = "sync_include_settings_no_api"
    const val KEY_SYNC_INCLUDE_API_KEYS = "sync_include_api_keys"
    const val KEY_SAVED_THEMES = "savedThemes"
    const val KEY_LAST_RECOMMENDATION_AT = "lastRecommendationAt"

    const val SUMMARY_CHANNEL_ID = "scheduled_summary_channel"
    const val SUMMARY_NOTIFICATION_ID = 1001
    const val MAX_ARTICLES_FOR_SUMMARIZATION = 15
    const val MAX_RETRY_ATTEMPTS = 2
}







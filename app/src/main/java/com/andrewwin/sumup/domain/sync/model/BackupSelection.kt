package com.andrewwin.sumup.domain.sync.model

data class BackupSelection(
    val includeSources: Boolean = true,
    val includeSubscriptions: Boolean = true,
    val includeSavedArticles: Boolean = true,
    val includeSettingsNoApi: Boolean = true,
    val includeApiKeys: Boolean = false
)

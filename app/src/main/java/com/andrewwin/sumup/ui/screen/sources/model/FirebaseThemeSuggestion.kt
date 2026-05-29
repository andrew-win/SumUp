package com.andrewwin.sumup.ui.screen.sources.model

import com.andrewwin.sumup.domain.repository.ImportedSourceGroup

data class FirebaseThemeSuggestion(
    val group: ImportedSourceGroup,
    val isSubscribed: Boolean,
    val isRecommended: Boolean
)

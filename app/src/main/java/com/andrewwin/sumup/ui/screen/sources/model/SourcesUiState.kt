package com.andrewwin.sumup.ui.screen.sources.model

import com.andrewwin.sumup.domain.source.model.SourceGroupWithSources

sealed interface SourcesUiState {
    data object Loading : SourcesUiState
    data class Content(
        val groups: List<SourceGroupWithSources>
    ) : SourcesUiState
}

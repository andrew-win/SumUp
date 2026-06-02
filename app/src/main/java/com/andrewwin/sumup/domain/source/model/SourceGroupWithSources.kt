package com.andrewwin.sumup.domain.source.model

data class SourceGroupWithSources(
    val group: SourceGroup,
    val sources: List<Source>
)

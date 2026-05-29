package com.andrewwin.sumup.domain.entities.source

data class SourceGroupWithSources(
    val group: SourceGroup,
    val sources: List<Source>
)

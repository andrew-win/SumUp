package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Source as RoomSource
import com.andrewwin.sumup.domain.entities.source.SourceGroupWithSources

fun GroupWithSources.toDomainModel(): SourceGroupWithSources = SourceGroupWithSources(
    group = group.toDomainModel(),
    sources = sources.map(RoomSource::toDomainModel)
)

package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Source as RoomSource
import com.andrewwin.sumup.data.local.entities.SourceGroup as RoomSourceGroup
import com.andrewwin.sumup.data.local.entities.SourceGroupOrigin as RoomSourceGroupOrigin
import com.andrewwin.sumup.data.local.entities.SourceType as RoomSourceType
import com.andrewwin.sumup.domain.source.Source
import com.andrewwin.sumup.domain.source.SourceGroup
import com.andrewwin.sumup.domain.source.SourceGroupOrigin
import com.andrewwin.sumup.domain.source.SourceGroupWithSources
import com.andrewwin.sumup.domain.source.SourceType

fun RoomSource.toDomainModel(): Source = Source(
    id = id,
    groupId = groupId,
    name = name,
    url = url,
    type = type.toDomainModel(),
    isEnabled = isEnabled,
    footerPattern = footerPattern,
    footerPatternCheckedAt = footerPatternCheckedAt,
    titleSelector = titleSelector,
    postLinkSelector = postLinkSelector,
    descriptionSelector = descriptionSelector,
    dateSelector = dateSelector,
    useHeadlessBrowser = useHeadlessBrowser
)

fun Source.toRoomEntity(): RoomSource = RoomSource(
    id = id,
    groupId = groupId,
    name = name,
    url = url,
    type = type.toRoomEntity(),
    isEnabled = isEnabled,
    footerPattern = footerPattern,
    footerPatternCheckedAt = footerPatternCheckedAt,
    titleSelector = titleSelector,
    postLinkSelector = postLinkSelector,
    descriptionSelector = descriptionSelector,
    dateSelector = dateSelector,
    useHeadlessBrowser = useHeadlessBrowser
)

fun RoomSourceGroup.toDomainModel(): SourceGroup = SourceGroup(
    id = id,
    name = name,
    isEnabled = isEnabled,
    isDeletable = isDeletable,
    origin = origin.toDomainModel(),
    subscriptionId = subscriptionId
)

fun SourceGroup.toRoomEntity(): RoomSourceGroup = RoomSourceGroup(
    id = id,
    name = name,
    isEnabled = isEnabled,
    isDeletable = isDeletable,
    origin = origin.toRoomEntity(),
    subscriptionId = subscriptionId
)

fun GroupWithSources.toDomainModel(): SourceGroupWithSources = SourceGroupWithSources(
    group = group.toDomainModel(),
    sources = sources.map(RoomSource::toDomainModel)
)

fun RoomSourceType.toDomainModel(): SourceType = when (this) {
    RoomSourceType.TELEGRAM -> SourceType.TELEGRAM
    RoomSourceType.RSS -> SourceType.RSS
    RoomSourceType.YOUTUBE -> SourceType.YOUTUBE
}

fun SourceType.toRoomEntity(): RoomSourceType = when (this) {
    SourceType.TELEGRAM -> RoomSourceType.TELEGRAM
    SourceType.RSS -> RoomSourceType.RSS
    SourceType.YOUTUBE -> RoomSourceType.YOUTUBE
}

fun String.toDomainSourceGroupOrigin(): SourceGroupOrigin = when (this) {
    RoomSourceGroupOrigin.PUBLIC_SUBSCRIPTION -> SourceGroupOrigin.PUBLIC_SUBSCRIPTION
    RoomSourceGroupOrigin.SYSTEM -> SourceGroupOrigin.SYSTEM
    else -> SourceGroupOrigin.USER
}

fun SourceGroupOrigin.toRoomEntity(): String = when (this) {
    SourceGroupOrigin.USER -> RoomSourceGroupOrigin.USER
    SourceGroupOrigin.PUBLIC_SUBSCRIPTION -> RoomSourceGroupOrigin.PUBLIC_SUBSCRIPTION
    SourceGroupOrigin.SYSTEM -> RoomSourceGroupOrigin.SYSTEM
}

private fun String.toDomainModel(): SourceGroupOrigin = toDomainSourceGroupOrigin()

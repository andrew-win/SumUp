package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.Source as RoomSource
import com.andrewwin.sumup.domain.entities.source.Source

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

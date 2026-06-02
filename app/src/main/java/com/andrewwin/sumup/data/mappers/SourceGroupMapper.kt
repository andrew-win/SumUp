package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.SourceGroup as RoomSourceGroup
import com.andrewwin.sumup.domain.source.model.SourceGroup

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

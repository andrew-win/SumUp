package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.SourceGroupOrigin as RoomSourceGroupOrigin
import com.andrewwin.sumup.domain.entities.source.SourceGroupOrigin

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

fun String.toDomainModel(): SourceGroupOrigin = toDomainSourceGroupOrigin()

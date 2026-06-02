package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.SourceType as RoomSourceType
import com.andrewwin.sumup.domain.source.model.SourceType

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

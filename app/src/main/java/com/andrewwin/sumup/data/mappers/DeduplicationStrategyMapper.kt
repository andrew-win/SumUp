package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.DeduplicationStrategy as RoomDeduplicationStrategy
import com.andrewwin.sumup.domain.settings.model.DeduplicationStrategy

fun RoomDeduplicationStrategy.toDomainModel(): DeduplicationStrategy = when (this) {
    RoomDeduplicationStrategy.CLOUD -> DeduplicationStrategy.CLOUD
    RoomDeduplicationStrategy.LOCAL -> DeduplicationStrategy.LOCAL
}

fun DeduplicationStrategy.toRoomEntity(): RoomDeduplicationStrategy = when (this) {
    DeduplicationStrategy.CLOUD -> RoomDeduplicationStrategy.CLOUD
    DeduplicationStrategy.LOCAL -> RoomDeduplicationStrategy.LOCAL
}

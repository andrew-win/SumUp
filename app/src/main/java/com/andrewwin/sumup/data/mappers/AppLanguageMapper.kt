package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.AppLanguage as RoomAppLanguage
import com.andrewwin.sumup.domain.settings.model.AppLanguage

fun RoomAppLanguage.toDomainModel(): AppLanguage = when (this) {
    RoomAppLanguage.UK -> AppLanguage.UK
    RoomAppLanguage.EN -> AppLanguage.EN
}

fun AppLanguage.toRoomEntity(): RoomAppLanguage = when (this) {
    AppLanguage.UK -> RoomAppLanguage.UK
    AppLanguage.EN -> RoomAppLanguage.EN
}

package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.AppThemeMode as RoomAppThemeMode
import com.andrewwin.sumup.domain.entities.settings.AppThemeMode

fun RoomAppThemeMode.toDomainModel(): AppThemeMode = when (this) {
    RoomAppThemeMode.SYSTEM -> AppThemeMode.SYSTEM
    RoomAppThemeMode.LIGHT -> AppThemeMode.LIGHT
    RoomAppThemeMode.DARK -> AppThemeMode.DARK
}

fun AppThemeMode.toRoomEntity(): RoomAppThemeMode = when (this) {
    AppThemeMode.SYSTEM -> RoomAppThemeMode.SYSTEM
    AppThemeMode.LIGHT -> RoomAppThemeMode.LIGHT
    AppThemeMode.DARK -> RoomAppThemeMode.DARK
}

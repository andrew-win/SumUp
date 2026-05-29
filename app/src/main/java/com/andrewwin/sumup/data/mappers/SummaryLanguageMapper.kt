package com.andrewwin.sumup.data.mappers

import com.andrewwin.sumup.data.local.entities.SummaryLanguage as RoomSummaryLanguage
import com.andrewwin.sumup.domain.entities.settings.SummaryLanguage

fun RoomSummaryLanguage.toDomainModel(): SummaryLanguage = when (this) {
    RoomSummaryLanguage.UK -> SummaryLanguage.UK
    RoomSummaryLanguage.EN -> SummaryLanguage.EN
}

fun SummaryLanguage.toRoomEntity(): RoomSummaryLanguage = when (this) {
    SummaryLanguage.UK -> RoomSummaryLanguage.UK
    SummaryLanguage.EN -> RoomSummaryLanguage.EN
}

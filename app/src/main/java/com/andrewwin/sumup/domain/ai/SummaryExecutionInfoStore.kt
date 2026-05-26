package com.andrewwin.sumup.domain.ai

import javax.inject.Inject
import javax.inject.Singleton

data class SummaryExecutionInfo(
    val label: String = "",
    val note: String = ""
)

@Singleton
class SummaryExecutionInfoStore @Inject constructor() {
    @Volatile
    private var currentInfo: SummaryExecutionInfo = SummaryExecutionInfo()

    fun update(info: SummaryExecutionInfo) {
        currentInfo = info
    }

    fun clear() {
        currentInfo = SummaryExecutionInfo()
    }

    fun current(): SummaryExecutionInfo = currentInfo
}

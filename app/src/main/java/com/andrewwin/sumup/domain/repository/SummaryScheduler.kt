package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.domain.entities.settings.ScheduledSummaryTime

interface SummaryScheduler {
    fun schedule(times: List<ScheduledSummaryTime>)
    fun prepareNow(scheduledAt: Long)
    fun deliverNow(scheduledAt: Long)
    fun cancel()
}







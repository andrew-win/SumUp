package com.andrewwin.sumup.domain.summary.repository

import com.andrewwin.sumup.domain.settings.model.ScheduledSummaryTime

interface SummaryScheduler {
    fun schedule(times: List<ScheduledSummaryTime>)
    fun prepareNow(scheduledAt: Long)
    fun deliverNow(scheduledAt: Long)
    fun cancel()
}







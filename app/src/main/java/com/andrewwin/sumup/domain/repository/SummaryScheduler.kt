package com.andrewwin.sumup.domain.repository

import com.andrewwin.sumup.data.local.entities.ScheduledSummaryTime

interface SummaryScheduler {
    fun schedule(times: List<ScheduledSummaryTime>)
    fun prepareNow(scheduledAt: Long)
    fun deliverNow(scheduledAt: Long)
    fun cancel()
}







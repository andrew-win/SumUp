package com.andrewwin.sumup.domain.repository

interface SummaryScheduler {
    fun schedule(hour: Int, minute: Int)
    fun cancel()
}

package com.andrewwin.sumup.worker.summary

object SummaryConstants {
    const val PREPARE_SCHEDULED_SUMMARY_WORK_NAME = "prepare_scheduled_summary"
    const val DELIVER_SCHEDULED_SUMMARY_WORK_NAME = "deliver_scheduled_summary"
    const val SCHEDULED_SUMMARY_WORK_TAG = "scheduled_summary_work"
    
    const val ACTION_PREPARE_SCHEDULED_SUMMARY = "com.andrewwin.sumup.action.PREPARE_SCHEDULED_SUMMARY"
    const val ACTION_DELIVER_SCHEDULED_SUMMARY = "com.andrewwin.sumup.action.DELIVER_SCHEDULED_SUMMARY"
    
    const val SCHEDULED_SUMMARY_PREPARE_ALARM_REQUEST_CODE = 2001
    const val SCHEDULED_SUMMARY_DELIVER_ALARM_REQUEST_CODE = 5001
    const val LEGACY_SCHEDULED_SUMMARY_DELIVER_ALARM_REQUEST_CODE = 2002
    
    const val SCHEDULED_SUMMARY_PREPARATION_LEAD_TIME_MINUTES = 30L

    fun scheduledSummaryWorkName(baseName: String, scheduledAt: Long): String = "${baseName}_$scheduledAt"
}

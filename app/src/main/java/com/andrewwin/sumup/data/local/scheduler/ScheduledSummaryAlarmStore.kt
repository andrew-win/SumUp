package com.andrewwin.sumup.data.local.scheduler

import android.content.Context
import com.andrewwin.sumup.domain.settings.model.ScheduledSummaryTime

class ScheduledSummaryAlarmStore(
    context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(times: List<ScheduledSummaryTime>) {
        val serializedTimes = times.joinToString(SEPARATOR) { "${it.hour}$PART_SEPARATOR${it.minute}" }
        preferences.edit().putString(KEY_SCHEDULED_TIMES, serializedTimes).apply()
    }

    fun read(): List<ScheduledSummaryTime> {
        val rawValue = preferences.getString(KEY_SCHEDULED_TIMES, null).orEmpty()
        if (rawValue.isBlank()) return emptyList()

        return rawValue.split(SEPARATOR)
            .mapNotNull { token ->
                val parts = token.split(PART_SEPARATOR)
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                ScheduledSummaryTime(hour = hour, minute = minute)
            }
    }

    fun clear() {
        preferences.edit().remove(KEY_SCHEDULED_TIMES).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "scheduled_summary_alarm_store"
        const val KEY_SCHEDULED_TIMES = "scheduled_times"
        const val SEPARATOR = ","
        const val PART_SEPARATOR = ":"
    }
}

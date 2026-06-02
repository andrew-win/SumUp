package com.andrewwin.sumup.domain.settings.model

data class ScheduledSummaryTime(
    val hour: Int,
    val minute: Int
) {
    fun toStorageValue(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    fun isValid(): Boolean = hour in 0..23 && minute in 0..59

    companion object {
        val DEFAULT = ScheduledSummaryTime(8, 0)

        fun fromStorageValue(value: String): ScheduledSummaryTime? {
            val parts = value.trim().split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            return ScheduledSummaryTime(hour, minute).takeIf { it.isValid() }
        }
    }
}

fun List<ScheduledSummaryTime>.normalizedScheduledSummaryTimes(): List<ScheduledSummaryTime> =
    asSequence()
        .filter { it.isValid() }
        .distinctBy { it.hour * 60 + it.minute }
        .sortedWith(compareBy<ScheduledSummaryTime> { it.hour }.thenBy { it.minute })
        .toList()

fun List<ScheduledSummaryTime>.toScheduledSummaryTimesStorageValue(): String =
    normalizedScheduledSummaryTimes().joinToString(",") { it.toStorageValue() }

fun String.toScheduledSummaryTimes(
    fallback: ScheduledSummaryTime = ScheduledSummaryTime.DEFAULT
): List<ScheduledSummaryTime> {
    val times = split(",")
        .mapNotNull(ScheduledSummaryTime::fromStorageValue)
        .normalizedScheduledSummaryTimes()
    return times.ifEmpty { listOf(fallback) }
}

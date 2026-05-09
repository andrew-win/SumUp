package com.andrewwin.sumup

import com.andrewwin.sumup.data.local.scheduler.ScheduledSummaryTimeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduledSummaryTimeCalculatorTest {
    private val calculator = ScheduledSummaryTimeCalculator()

    @Test
    fun nextTriggerAtMillis_returnsTodayWhenTimeIsAhead() {
        val now = timestamp(day = 10, hour = 7, minute = 30)
        val expected = timestamp(day = 10, hour = 8, minute = 0)

        val actual = calculator.nextTriggerAtMillis(hour = 8, minute = 0, nowMillis = now)

        assertEquals(expected, actual)
    }

    @Test
    fun nextTriggerAtMillis_returnsTomorrowWhenTimeAlreadyPassed() {
        val now = timestamp(day = 10, hour = 9, minute = 0)
        val expected = timestamp(day = 11, hour = 8, minute = 0)

        val actual = calculator.nextTriggerAtMillis(hour = 8, minute = 0, nowMillis = now)

        assertEquals(expected, actual)
    }

    @Test
    fun wasTodayTriggerMissed_returnsTrueWhenTodayRunDidNotHappen() {
        val now = timestamp(day = 10, hour = 9, minute = 0)
        val lastRun = timestamp(day = 9, hour = 8, minute = 0)

        val missed = calculator.wasTodayTriggerMissed(
            hour = 8,
            minute = 0,
            lastRunTimestamp = lastRun,
            nowMillis = now
        )

        assertTrue(missed)
    }

    @Test
    fun wasTodayTriggerMissed_returnsFalseWhenTodayRunAlreadyHappened() {
        val now = timestamp(day = 10, hour = 9, minute = 0)
        val lastRun = timestamp(day = 10, hour = 8, minute = 5)

        val missed = calculator.wasTodayTriggerMissed(
            hour = 8,
            minute = 0,
            lastRunTimestamp = lastRun,
            nowMillis = now
        )

        assertFalse(missed)
    }

    private fun timestamp(day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

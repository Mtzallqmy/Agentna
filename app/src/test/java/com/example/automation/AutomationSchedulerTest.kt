package com.mtzallqmy.agentna.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AutomationSchedulerTest {
    private val zone = ZoneId.of("Asia/Aden")

    @Test fun schedulesLaterSameDay() {
        val now = ZonedDateTime.of(2026, 9, 2, 7, 15, 0, 0, zone).toInstant().toEpochMilli()
        val next = AutomationScheduler.nextRunEpochMillis("0 8 * * *", now, zone)!!
        val scheduled = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(next), zone)
        assertEquals(8, scheduled.hour)
        assertEquals(0, scheduled.minute)
        assertEquals(2, scheduled.dayOfMonth)
    }

    @Test fun rollsToNextDayWhenTimePassed() {
        val now = ZonedDateTime.of(2026, 9, 2, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val next = AutomationScheduler.nextRunEpochMillis("0 8 * * *", now, zone)!!
        val scheduled = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(next), zone)
        assertEquals(3, scheduled.dayOfMonth)
        assertEquals(8, scheduled.hour)
    }

    @Test fun rejectsUnsupportedCronRatherThanGuessing() {
        assertNull(AutomationScheduler.nextRunEpochMillis("*/5 * * * *", System.currentTimeMillis(), zone))
        assertTrue(AutomationScheduler.describe("*/5 * * * *").startsWith("Unsupported"))
    }
}

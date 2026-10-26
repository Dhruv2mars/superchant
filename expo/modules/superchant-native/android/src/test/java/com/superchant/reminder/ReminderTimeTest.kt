package com.superchant.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimeTest {

    private val zone = ZoneId.of("America/New_York")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), zone)

    @Test
    fun laterTodayOrTomorrow() {
        assertEquals(at(2026, 10, 18, 6, 0), ReminderTime.nextTrigger(at(2026, 10, 18, 5, 0), 6 * 60))
        assertEquals(at(2026, 10, 19, 6, 0), ReminderTime.nextTrigger(at(2026, 10, 18, 7, 0), 6 * 60))
        // Exactly at the time means it just fired: next is tomorrow
        assertEquals(at(2026, 10, 19, 6, 0), ReminderTime.nextTrigger(at(2026, 10, 18, 6, 0), 6 * 60))
        assertEquals(at(2026, 12, 31, 23, 59), ReminderTime.nextTrigger(at(2026, 12, 31, 23, 0), 23 * 60 + 59))
        assertEquals(at(2027, 1, 1, 0, 0), ReminderTime.nextTrigger(at(2026, 12, 31, 23, 0), 0))
    }

    @Test
    fun daylightSavingGapMovesLaterAndOverlapFiresOnce() {
        // 2:30 doesn't exist on 2026-03-08 in New York; it fires at 3:30 instead
        val spring = ReminderTime.nextTrigger(at(2026, 3, 8, 1, 0), 2 * 60 + 30)
        assertEquals(3, spring.hour)
        assertEquals(30, spring.minute)
        // 1:30 happens twice on 2026-11-01; the next trigger after the first is the next day
        val first = ReminderTime.nextTrigger(at(2026, 11, 1, 0, 0), 90)
        val next = ReminderTime.nextTrigger(first, 90)
        assertEquals(2, next.dayOfMonth)
    }

    @Test
    fun syncKeepsADueReminderThatHasNotFiredYet() {
        val six = 6 * 60
        fun millis(t: ZonedDateTime) = t.toInstant().toEpochMilli()
        val firedYesterday = millis(at(2026, 10, 17, 6, 3))
        // Opening the app at 6:05 before today's 6:00 alarm was delivered keeps it (it fires right away)
        assertEquals(at(2026, 10, 18, 6, 0), ReminderTime.syncTrigger(at(2026, 10, 18, 6, 5), six, firedYesterday))
        assertEquals(at(2026, 10, 18, 6, 0), ReminderTime.syncTrigger(at(2026, 10, 18, 6, 5), six, 0))
        assertEquals(at(2026, 10, 18, 6, 0), ReminderTime.syncTrigger(at(2026, 10, 18, 5, 0), six, firedYesterday))
        // Already fired, or the setting just changed: tomorrow's
        assertEquals(at(2026, 10, 19, 6, 0), ReminderTime.syncTrigger(at(2026, 10, 18, 6, 5), six, millis(at(2026, 10, 18, 6, 1))))
        // Past the delivery window nothing is sent late
        assertEquals(at(2026, 10, 19, 6, 0), ReminderTime.syncTrigger(at(2026, 10, 18, 6, 30), six, firedYesterday))
        // The clock was set back: a handled time in the future counts as now
        val future = millis(at(2026, 10, 25, 6, 0))
        assertEquals(at(2026, 10, 18, 6, 0), ReminderTime.syncTrigger(at(2026, 10, 18, 5, 0), six, future))
        assertEquals(at(2026, 10, 19, 6, 0), ReminderTime.syncTrigger(at(2026, 10, 18, 6, 5), six, future))
    }

    @Test
    fun validMinutes() {
        assertTrue(ReminderTime.isValid(0))
        assertTrue(ReminderTime.isValid(1439))
        assertFalse(ReminderTime.isValid(1440))
        assertFalse(ReminderTime.isValid(-1))
    }
}

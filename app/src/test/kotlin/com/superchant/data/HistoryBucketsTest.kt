package com.superchant.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class HistoryBucketsTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long = Calendar.getInstance(utc).apply {
        clear()
        set(year, month - 1, day, hour, 0)
    }.timeInMillis

    private fun record(id: String, start: Long) = SessionRecord(id = id, startTime = start, endTime = start, totalCounts = 1, totalRounds = 0)

    @Test
    fun bucketsUseLocaleFirstDayOfWeek() {
        // Wednesday 15 Oct 2025, 10:00
        val now = at(2025, 10, 15, hour = 10)
        val sessions = listOf(
            record("today", at(2025, 10, 15, hour = 0)),
            record("mon", at(2025, 10, 13)),
            record("sun", at(2025, 10, 12)),
            record("oct1", at(2025, 10, 1)),
            record("sep", at(2025, 9, 30))
        )

        val us = bucketSessions(sessions, now, utc, Locale.US) // weeks start on Sunday
        assertEquals(listOf("today"), us.today.map { it.id })
        assertEquals(listOf("mon", "sun"), us.thisWeek.map { it.id })
        assertEquals(listOf("oct1"), us.thisMonth.map { it.id })
        assertEquals(listOf("sep"), us.older.map { it.id })

        val germany = bucketSessions(sessions, now, utc, Locale.GERMANY) // weeks start on Monday
        assertEquals(listOf("mon"), germany.thisWeek.map { it.id })
        assertEquals(listOf("sun", "oct1"), germany.thisMonth.map { it.id })
    }

    @Test
    fun weekSpanningMonthsCountsAsThisWeek() {
        // Thursday 2 Oct 2025; the US week started Sunday 28 Sep
        val now = at(2025, 10, 2)
        val buckets = bucketSessions(listOf(record("sep29", at(2025, 9, 29)), record("sep27", at(2025, 9, 27))), now, utc, Locale.US)
        assertEquals(listOf("sep29"), buckets.thisWeek.map { it.id })
        assertEquals(listOf("sep27"), buckets.older.map { it.id })
    }

    @Test
    fun todayMovesToThisWeekAfterMidnight() {
        val session = listOf(record("late", at(2025, 10, 15, hour = 23)))
        assertEquals(1, bucketSessions(session, at(2025, 10, 15, hour = 23) + 60_000, utc, Locale.US).today.size)
        assertEquals(1, bucketSessions(session, at(2025, 10, 16, hour = 0) + 60_000, utc, Locale.US).thisWeek.size)
    }

    @Test
    fun millisUntilNextMidnightIsExact() {
        assertEquals(60_000L, millisUntilNextMidnight(at(2025, 10, 16, hour = 0) - 60_000, utc))
        assertEquals(24 * 3_600_000L, millisUntilNextMidnight(at(2025, 10, 16, hour = 0), utc))
    }
}

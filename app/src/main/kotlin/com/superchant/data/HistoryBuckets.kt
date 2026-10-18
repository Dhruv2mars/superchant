package com.superchant.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** History grouped by recency. Each session is in exactly one bucket. */
data class HistoryBuckets(
    val today: List<SessionRecord>,
    val thisWeek: List<SessionRecord>,
    val thisMonth: List<SessionRecord>,
    val older: List<SessionRecord>
)

/**
 * Groups [sessions] (kept in their given order) relative to [now]:
 * - today: since local midnight
 * - thisWeek: since midnight on the first day of the current week, using the locale's first day
 *   of the week (Sunday in the US, Monday in most of Europe)
 * - thisMonth: since midnight on the 1st of the current month
 * - older: everything before
 * When the week started in the previous month, "this week" also covers those days.
 */
fun bucketSessions(
    sessions: List<SessionRecord>,
    now: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault()
): HistoryBuckets {
    fun midnight(adjust: Calendar.() -> Unit = {}) = Calendar.getInstance(timeZone, locale).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        adjust()
    }.timeInMillis
    
    val startOfToday = midnight()
    // Setting DAY_OF_WEEK moves within the current week as defined by firstDayOfWeek.
    val startOfWeek = midnight { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }
    val startOfMonth = midnight { set(Calendar.DAY_OF_MONTH, 1) }
    
    val today = mutableListOf<SessionRecord>()
    val thisWeek = mutableListOf<SessionRecord>()
    val thisMonth = mutableListOf<SessionRecord>()
    val older = mutableListOf<SessionRecord>()
    
    for (session in sessions) {
        when {
            session.startTime >= startOfToday -> today.add(session)
            session.startTime >= startOfWeek -> thisWeek.add(session)
            session.startTime >= startOfMonth -> thisMonth.add(session)
            else -> older.add(session)
        }
    }
    
    return HistoryBuckets(today, thisWeek, thisMonth, older)
}

/** Milliseconds from [now] until the next local midnight. */
fun millisUntilNextMidnight(now: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
    Calendar.getInstance(timeZone).apply {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis - now

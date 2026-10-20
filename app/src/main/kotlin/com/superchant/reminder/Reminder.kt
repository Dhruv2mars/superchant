package com.superchant.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.superchant.MainActivity
import com.superchant.R
import com.superchant.data.PracticeStats
import com.superchant.data.PreferencesRepository
import com.superchant.service.ChantingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** When the daily reminder fires. Pure, so DST and day boundaries are unit tested. */
object ReminderTime {
    const val DEFAULT_MINUTE_OF_DAY = 6 * 60

    fun isValid(minuteOfDay: Int) = minuteOfDay in 0 until 24 * 60

    /** How late yesterday's reminder (or one never marked handled) is still kept: the alarm's delivery window. */
    const val GRACE_MINUTES = 15L

    /** The next time strictly after [now] at [minuteOfDay] local time (a DST gap moves it later). */
    fun nextTrigger(now: ZonedDateTime, minuteOfDay: Int): ZonedDateTime {
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        val today = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
        return if (today.isAfter(now)) today else ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
    }

    /**
     * The reminder to schedule when re-syncing at [now] (opening the app, boot, an update, a clock change).
     * Today's reminder that hasn't been handled (fired, or the setting changed) since its time is kept,
     * however late, so the alarm still fires: Doze and battery saver can hold it for hours, and re-syncing
     * used to replace it with tomorrow's. Yesterday's is kept only within [GRACE_MINUTES]. [handledThrough]
     * is epoch millis; 0 (never recorded, set up before it was) also keeps only [GRACE_MINUTES], and a
     * value ahead of [now] (the clock was set back) counts as now.
     */
    fun syncTrigger(now: ZonedDateTime, minuteOfDay: Int, handledThrough: Long): ZonedDateTime {
        val graceStart = now.minusMinutes(GRACE_MINUTES)
        // Just before midnight, so a reminder at 0:00 is today's
        val today = now.toLocalDate().atStartOfDay(now.zone).minusNanos(1)
        val keepFrom = if (handledThrough == 0L || graceStart.isBefore(today)) graceStart else today
        val handled = Instant.ofEpochMilli(minOf(handledThrough, now.toInstant().toEpochMilli())).atZone(now.zone)
        return nextTrigger(if (handled.isAfter(keepFrom)) handled else keepFrom, minuteOfDay)
    }
}

/**
 * Schedules the gentle daily reminder with an inexact alarm (a 15-minute window), which needs no
 * exact-alarm permission and lets the system batch it. One alarm at a time; each firing schedules
 * the next, and boot, app updates and clock changes reschedule it.
 */
object ReminderScheduler {
    private const val WINDOW_MS = 15 * 60 * 1000L
    private const val REQUEST_CODE = 200

    fun schedule(context: Context, minuteOfDay: Int, now: ZonedDateTime = ZonedDateTime.now()) {
        setAlarm(context, ReminderTime.nextTrigger(now, minuteOfDay))
    }

    private fun setAlarm(context: Context, trigger: ZonedDateTime) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), WINDOW_MS, pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    /** Matches the alarm to the stored settings, keeping one that is due but hasn't fired yet. */
    suspend fun sync(context: Context) {
        val (settings, handledThrough) = PreferencesRepository.get(context).afterWrites { loadReminder() }
        if (!settings.enabled) return cancel(context)
        setAlarm(context, ReminderTime.syncTrigger(ZonedDateTime.now(), settings.minuteOfDay, handledThrough))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Re-arms the reminder after boot, app updates and clock or time zone changes (protected system broadcasts). */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for these system broadcasts only; ignore anything else sent to it
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val appContext = context.applicationContext
        runAsync { ReminderScheduler.sync(appContext) }
    }
}

private val RESCHEDULE_ACTIONS = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED
)

/**
 * The alarm itself (not exported). Posts the reminder unless it isn't wanted: turned off, a session is
 * running, or today's practice (the daily target, or any counts without one) is already done. Then
 * schedules tomorrow's.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val appContext = context.applicationContext
        runAsync {
            val firedAt = System.currentTimeMillis()
            remindIfNeeded(appContext)
            // Today's reminder is done, so the sync below schedules tomorrow's
            PreferencesRepository.get(appContext).write { setReminderHandledThrough(firedAt) }
            ReminderScheduler.sync(appContext)
        }
    }

    private suspend fun remindIfNeeded(context: Context) {
        val repository = PreferencesRepository.get(context)
        val settings = repository.reminder.filterNotNull().first()
        if (!settings.enabled || ChantingService.isRunning) return
        val target = repository.dailyTarget.filterNotNull().first()
        val history = repository.sessionHistory.filterNotNull().first()
        val today = LocalDate.now()
        val todayTotal = PracticeStats.dailyTotals(history)[today]
        if (PracticeStats.isPracticeDay(todayTotal, target)) return
        val streak = PracticeStats.streak(PracticeStats.dailyTotals(history), today, target).current
        post(context, target, todayTotal?.rounds ?: 0, streak)
    }

    private fun post(context: Context, target: Int, roundsToday: Int, streak: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.reminder_channel_description)
                setShowBadge(false)
            }
        )
        val text = when {
            streak > 0 -> context.resources.getQuantityString(R.plurals.reminder_text_streak, streak, streak)
            target > 0 -> context.resources.getQuantityString(R.plurals.reminder_text_target, target, roundsToday, target)
            else -> context.getString(R.string.reminder_text)
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_REMIND = "com.superchant.action.REMIND"
        private const val CHANNEL_ID = "daily_reminder"
        private const val NOTIFICATION_ID = 2
    }
}

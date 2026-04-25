package com.rabbithole.musicbbit.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rabbithole.musicbbit.data.model.AlarmEntity
import timber.log.Timber
import java.util.Calendar
import com.rabbithole.musicbbit.domain.usecase.IsWorkdayUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton wrapper around [AlarmManager] for scheduling, cancelling, and rescheduling alarms.
 *
 * Each alarm is mapped to a [PendingIntent] targeting [AlarmReceiver].
 * The alarm ID is used as the request code to ensure unique PendingIntents.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val isWorkdayUseCase: IsWorkdayUseCase
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Check whether the app can schedule exact alarms on API 31+.
     *
     * On API < 31 this always returns true because the permission is not required.
     */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    /**
     * Schedule a single alarm with the system [AlarmManager].
     *
     * If the alarm is disabled, it will be cancelled instead.
     * Considers Chinese holidays and adjusted workdays when calculating the next trigger time.
     *
     * @param alarm The alarm entity to schedule.
     */
    suspend fun schedule(alarm: AlarmEntity) {
        if (!alarm.isEnabled) {
            Timber.d("Alarm ${alarm.id} is disabled, cancelling instead of scheduling")
            cancel(alarm.id)
            return
        }

        val triggerTime = calculateNextTriggerTimeWithHolidays(
            alarm.hour,
            alarm.minute,
            alarm.repeatDaysBitmask
        )

        val pendingIntent = createPendingIntent(alarm.id)

        Timber.i(
            "Scheduling alarm id=${alarm.id} at ${alarm.hour}:${alarm.minute} " +
                "(triggerTime=$triggerTime, repeatMask=${alarm.repeatDaysBitmask})"
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    /**
     * Cancel a previously scheduled alarm.
     *
     * @param alarmId The ID of the alarm to cancel.
     */
    fun cancel(alarmId: Long) {
        Timber.i("Cancelling alarm id=$alarmId")
        val pendingIntent = createPendingIntent(alarmId)
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Reschedule all enabled alarms.
     *
     * First cancels every alarm in the list, then re-schedules each enabled one.
     * This is useful after device reboot or when the app needs to refresh all alarms.
     *
     * @param enabledAlarms List of currently enabled alarm entities.
     */
    suspend fun rescheduleAll(enabledAlarms: List<AlarmEntity>) {
        Timber.i("Rescheduling all ${enabledAlarms.size} enabled alarms")

        // Cancel all first to avoid duplicate PendingIntents
        enabledAlarms.forEach { cancel(it.id) }

        // Re-schedule each enabled alarm
        enabledAlarms.forEach { schedule(it) }
    }

    /**
     * Calculate the next trigger time considering Chinese holidays and adjusted workdays.
     *
     * For one-time alarms: finds the next occurrence that is a workday.
     * For repeating alarms: finds the next matching day that is also a workday.
     * If a matching day is a statutory holiday, it is skipped.
     * If a non-matching day is an adjusted workday, it is included.
     *
     * @param hour Hour of day in 24-hour format.
     * @param minute Minute of hour.
     * @param repeatDaysBitmask Bitmask of repeating days. 0 means one-time.
     * @return Unix timestamp (ms) of the next trigger time.
     */
    private suspend fun calculateNextTriggerTimeWithHolidays(
        hour: Int,
        minute: Int,
        repeatDaysBitmask: Int
    ): Long {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // For one-time alarms, ensure candidate is in the future
        if (repeatDaysBitmask == 0 && candidate.before(now)) {
            candidate.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Find the next valid trigger date
        while (true) {
            val dateStr = String.format(
                "%04d-%02d-%02d",
                candidate.get(Calendar.YEAR),
                candidate.get(Calendar.MONTH) + 1,
                candidate.get(Calendar.DAY_OF_MONTH)
            )
            val isWorkday = isWorkdayUseCase(
                java.time.LocalDate.parse(dateStr)
            )
            val dayMatches = isDayMatchingBitmask(candidate, repeatDaysBitmask)
                || repeatDaysBitmask == 0

            if (candidate.before(now)) {
                // Time already passed, move to next day
                candidate.add(Calendar.DAY_OF_MONTH, 1)
                continue
            }

            if (repeatDaysBitmask == 0) {
                // One-time alarm: any workday is fine
                if (isWorkday) {
                    return candidate.timeInMillis
                }
            } else {
                // Repeating alarm:
                // - If the day matches the repeat pattern AND is a workday -> trigger
                // - If the day does NOT match but is an adjusted workday -> trigger
                if (dayMatches && isWorkday) {
                    return candidate.timeInMillis
                }
                if (!dayMatches && isWorkday) {
                    // Check if this is an adjusted workday (Saturday/Sunday that is workday)
                    val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
                    if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                        return candidate.timeInMillis
                    }
                }
            }

            candidate.add(Calendar.DAY_OF_MONTH, 1)

            // Safety limit: don't search more than 2 years ahead
            val maxSearchDays = 730
            if (candidate.get(Calendar.YEAR) > now.get(Calendar.YEAR) + 1) {
                Timber.w("Could not find valid workday within $maxSearchDays days, falling back to basic calculation")
                return calculateNextTriggerTime(hour, minute, repeatDaysBitmask, now)
            }
        }
    }

    /**
     * Create a [PendingIntent] for the given alarm ID.
     *
     * @param alarmId The alarm ID used as the request code.
     * @return A PendingIntent targeting [AlarmReceiver].
     */
    private fun createPendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"

        /**
         * Calculate the next trigger time in milliseconds for an alarm.
         *
         * For one-time alarms (repeatDaysBitmask == 0), the trigger time is set to
         * the next occurrence of the specified hour:minute. If that time has already
         * passed today, it moves to tomorrow.
         *
         * For repeating alarms, it finds the next matching day of the week.
         *
         * @param hour Hour of day in 24-hour format (0-23).
         * @param minute Minute of hour (0-59).
         * @param repeatDaysBitmask Bitmask of repeating days. 0 means one-time.
         *                          bit0=Monday, bit1=Tuesday, ..., bit6=Sunday.
         * @return Unix timestamp (ms) of the next trigger time.
         */
        fun calculateNextTriggerTime(hour: Int, minute: Int, repeatDaysBitmask: Int): Long {
            return calculateNextTriggerTime(hour, minute, repeatDaysBitmask, Calendar.getInstance())
        }

        /**
         * Test-visible overload that allows injecting a fixed "now" time.
         */
        fun calculateNextTriggerTime(
            hour: Int,
            minute: Int,
            repeatDaysBitmask: Int,
            now: Calendar
        ): Long {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (repeatDaysBitmask == 0) {
                // One-time alarm: if the time has already passed today, move to tomorrow
                if (candidate.before(now)) {
                    candidate.add(Calendar.DAY_OF_MONTH, 1)
                }
            } else {
                // Repeating alarm: find the next matching day of the week
                while (candidate.before(now) || !isDayMatchingBitmask(candidate, repeatDaysBitmask)) {
                    candidate.add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            return candidate.timeInMillis
        }

        /**
         * Check if the given calendar day matches the repeat days bitmask.
         *
         * Calendar day constants: MONDAY=2, TUESDAY=3, ..., SUNDAY=1.
         * Bitmask: bit0=Monday, bit1=Tuesday, ..., bit6=Sunday.
         *
         * @param calendar The calendar instance to check.
         * @param bitmask The repeat days bitmask.
         * @return True if the day matches the bitmask.
         */
        private fun isDayMatchingBitmask(calendar: Calendar, bitmask: Int): Boolean {
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val bit = when (dayOfWeek) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> return false
            }
            return bitmask and (1 shl bit) != 0
        }
    }
}

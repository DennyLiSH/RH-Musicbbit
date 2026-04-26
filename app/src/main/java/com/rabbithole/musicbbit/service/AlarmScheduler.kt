package com.rabbithole.musicbbit.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.service.alarm.NextOccurrenceCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Singleton wrapper around [AlarmManager] for scheduling, cancelling, and rescheduling alarms.
 *
 * Each alarm is mapped to a [PendingIntent] targeting [AlarmReceiver].
 * The alarm ID is used as the request code to ensure unique PendingIntents.
 *
 * Trigger-time math is delegated to [NextOccurrenceCalculator].
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val nextOccurrenceCalculator: NextOccurrenceCalculator,
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

        val triggerTime = nextOccurrenceCalculator.nextOccurrence(
            alarm.hour,
            alarm.minute,
            alarm.repeatDaysBitmask,
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
    }
}

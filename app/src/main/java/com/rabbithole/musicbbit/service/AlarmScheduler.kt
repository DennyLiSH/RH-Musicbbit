package com.rabbithole.musicbbit.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.service.alarm.AlarmIntegrityWorker
import com.rabbithole.musicbbit.service.alarm.NextOccurrenceCalculator
import com.rabbithole.musicbbit.service.alarm.ports.PermissionPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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
    private val permissionPort: PermissionPort,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Whether the app can schedule exact alarms — delegated to [PermissionPort],
     * the single source of truth for permission queries (API 31 branch lives there).
     */
    fun canScheduleExactAlarms(): Boolean = permissionPort.canScheduleExactAlarms()

    /**
     * Schedule a single alarm with the system [AlarmManager].
     *
     * If the alarm is disabled, it will be cancelled instead.
     * Considers Chinese holidays and adjusted workdays when calculating the next trigger time.
     *
     * If [NextOccurrenceCalculator.nextOccurrence] cannot find a valid ring day within the
     * 2-year search window, it throws [IllegalStateException]; this method catches that
     * exception, logs it, and skips scheduling this single alarm — letting callers
     * (e.g. [AlarmStartupReconciler.reconcileAll]) continue with other alarms.
     *
     * @param alarm The alarm to schedule.
     */
    suspend fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) {
            Timber.d("Alarm ${alarm.id} is disabled, cancelling instead of scheduling")
            cancel(alarm.id)
            return
        }

        val triggerTime = try {
            nextOccurrenceCalculator.nextOccurrence(
                alarm.hour,
                alarm.minute,
                alarm.repeatDays,
                alarm.excludeHolidays,
            )
        } catch (e: IllegalStateException) {
            Timber.e(e, "Skipping alarm ${alarm.id}: no valid ring day within 2-year search window")
            return
        }

        val pendingIntent = createPendingIntent(alarm.id)

        Timber.i(
            "Scheduling alarm id=${alarm.id} at ${alarm.hour}:${alarm.minute} " +
                "(triggerTime=$triggerTime, repeatDays=${alarm.repeatDays})"
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, createShowIntent())
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
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
     * @param enabledAlarms List of currently enabled alarms.
     */
    suspend fun rescheduleAll(enabledAlarms: List<Alarm>) {
        Timber.i("Rescheduling all ${enabledAlarms.size} enabled alarms")

        coroutineScope {
            // Cancel all first to avoid duplicate PendingIntents
            enabledAlarms.map { async { cancel(it.id) } }.awaitAll()

            // Re-schedule each enabled alarm
            enabledAlarms.map { async { schedule(it) } }.awaitAll()
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

    /**
     * Create a [PendingIntent] that opens the app when the user taps the alarm icon
     * in the status bar. Used as the [AlarmManager.AlarmClockInfo] show intent.
     */
    private fun createShowIntent(): PendingIntent {
        val intent = Intent(context, com.rabbithole.musicbbit.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Schedule a periodic integrity check via WorkManager.
     *
     * Uses [ExistingPeriodicWorkPolicy.KEEP] so the 15-minute interval is not reset
     * if a previous request is still active.
     */
    fun scheduleIntegrityCheck() {
        val request = PeriodicWorkRequestBuilder<AlarmIntegrityWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AlarmIntegrityWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Timber.i("AlarmScheduler: scheduled periodic integrity check (15min)")
    }

    /**
     * Cancel the periodic integrity check.
     */
    fun cancelIntegrityCheck() {
        WorkManager.getInstance(context).cancelUniqueWork(AlarmIntegrityWorker.WORK_NAME)
        Timber.i("AlarmScheduler: cancelled periodic integrity check")
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}

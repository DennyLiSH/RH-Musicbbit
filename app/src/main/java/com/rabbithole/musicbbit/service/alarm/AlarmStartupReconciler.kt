package com.rabbithole.musicbbit.service.alarm

import androidx.annotation.VisibleForTesting
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.service.AlarmScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciles alarm state with the system scheduler on app startup.
 *
 * When [bookkeepAlarmTrigger] fails in [AlarmFireSession] (e.g. DB exception),
 * one-shot alarms may remain enabled even though they already fired, and
 * repeating alarms may lose their system-side [PendingIntent]. This class
 * scans all enabled alarms on startup and repairs those inconsistencies.
 *
 * - One-shot alarm that has already triggered (lastTriggeredAt != null) but
 *   is still enabled -> disable it.
 * - Repeating alarm -> unconditionally reschedule (idempotent).
 * - One-shot alarm not yet triggered -> no action needed.
 *
 * The work is launched on a background scope so [Application.onCreate] is not blocked.
 */
@Singleton
class AlarmStartupReconciler @Inject constructor(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /**
     * Launch the reconciliation asynchronously.
     *
     * Call from [Application.onCreate] — it returns immediately.
     */
    fun reconcile() {
        scope.launch {
            reconcileInternal()
        }
    }

    @VisibleForTesting
    suspend fun reconcileInternal() {
        try {
            val enabledAlarms = alarmDao.getEnabledAlarms().first()
            Timber.i("AlarmStartupReconciler: scanning ${enabledAlarms.size} enabled alarms")

            for (alarm in enabledAlarms) {
                try {
                    when {
                        // One-shot alarm that has already triggered but was not disabled
                        // (bookkeep failed after playback started)
                        alarm.repeatDaysBitmask == 0 && alarm.lastTriggeredAt != null -> {
                            Timber.w(
                                "AlarmStartupReconciler: disabling one-shot alarm ${alarm.id} " +
                                    "(lastTriggeredAt=${alarm.lastTriggeredAt})"
                            )
                            alarmDao.update(alarm.copy(isEnabled = false))
                        }

                        // Repeating alarm: ensure system-side schedule is up to date
                        alarm.repeatDaysBitmask != 0 -> {
                            Timber.i("AlarmStartupReconciler: rescheduling repeating alarm ${alarm.id}")
                            alarmScheduler.rescheduleAll(listOf(alarm))
                        }

                        // One-shot not yet triggered: nothing to do
                        else -> {
                            Timber.d(
                                "AlarmStartupReconciler: one-shot alarm ${alarm.id} not yet triggered, skipping"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "AlarmStartupReconciler: failed to process alarm ${alarm.id}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "AlarmStartupReconciler: failed to reconcile alarms")
        }
    }
}

package com.rabbithole.musicbbit.service.alarm

import androidx.annotation.VisibleForTesting
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.service.AlarmScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
 * - One-shot alarm not yet triggered -> reschedule (PendingIntent may be lost after reboot or process kill).
 *
 * The work is launched on a background scope so [Application.onCreate] is not blocked.
 */
@Singleton
class AlarmStartupReconciler @Inject constructor(
    private val alarmRecovery: AlarmRecovery,
    private val alarmScheduler: AlarmScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /**
     * Launch the reconciliation asynchronously.
     *
     * Call from [Application.onCreate] — it returns immediately.
     *
     * Failures are contained here: this coroutine is launched from Application.onCreate
     * as a best-effort repair, so an uncaught exception would crash the app over a
     * recoverable inconsistency that the next launch (or the integrity worker) retries.
     */
    fun reconcile() {
        scope.launch {
            try {
                reconcileInternal()
                alarmScheduler.scheduleIntegrityCheck()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Startup alarm reconciliation failed; will retry on next launch")
            }
        }
    }

    @VisibleForTesting
    suspend fun reconcileInternal() {
        // Repair loop lives in AlarmRecovery — shared with BootReceiver and the
        // integrity worker. This class only owns WHEN startup recovery runs.
        alarmRecovery.recoverAll()
    }
}

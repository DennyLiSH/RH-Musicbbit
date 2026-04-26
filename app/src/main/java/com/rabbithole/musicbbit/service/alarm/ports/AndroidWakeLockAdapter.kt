package com.rabbithole.musicbbit.service.alarm.ports

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * [WakeLockPort] adapter backed by [PowerManager.WakeLock] (PARTIAL_WAKE_LOCK).
 *
 * Singleton — a single wake lock instance is reused for the application lifetime, with
 * `setReferenceCounted(false)` so [acquire] always extends the timeout instead of stacking.
 */
@Singleton
class AndroidWakeLockAdapter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WakeLockPort {

    private var wakeLock: PowerManager.WakeLock? = null

    override val isHeld: Boolean
        get() = wakeLock?.isHeld == true

    override fun acquire(timeoutMs: Long) {
        try {
            if (wakeLock == null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                    .apply { setReferenceCounted(false) }
            }
            wakeLock?.acquire(timeoutMs)
            Timber.d("Alarm wake lock acquired (timeout=${timeoutMs}ms)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire alarm wake lock")
        }
    }

    override fun release() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.d("Alarm wake lock released")
            }
        }
    }

    companion object {
        private const val WAKE_LOCK_TAG = "RH-Musicbbit::AlarmPlaybackWakeLock"
    }
}

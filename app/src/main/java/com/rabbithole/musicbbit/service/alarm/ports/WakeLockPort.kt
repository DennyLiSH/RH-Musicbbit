package com.rabbithole.musicbbit.service.alarm.ports

/**
 * Abstraction over a partial wake lock used to keep the CPU awake while an alarm session
 * is active.
 *
 * Production adapter: [AndroidWakeLockAdapter] — wraps a [android.os.PowerManager.WakeLock].
 * Test adapter: a fake that records `acquire`/`release` calls.
 *
 * Lifetime: a single underlying lock is reused across [acquire]/[release] cycles. Calling
 * [acquire] while the lock is already held refreshes the timeout.
 */
interface WakeLockPort {

    /**
     * Whether the underlying wake lock is currently held.
     */
    val isHeld: Boolean

    /**
     * Acquire the wake lock with the given timeout (milliseconds). Idempotent.
     *
     * @param timeoutMs Maximum duration to hold the lock if not explicitly released.
     */
    fun acquire(timeoutMs: Long)

    /**
     * Release the wake lock if held. No-op otherwise.
     */
    fun release()
}

package com.rabbithole.musicbbit.service.alarm.ports

import kotlinx.coroutines.CoroutineScope

/**
 * Abstraction over the alarm volume-ramp feature.
 *
 * The ramp gradually raises the music stream volume from a low starting level toward the
 * device maximum, aborting if the user adjusts volume manually.
 *
 * Production adapter: [com.rabbithole.musicbbit.service.AlarmVolumeController].
 * Test adapter: a fake recording start/restore calls.
 */
interface VolumeRampPort {

    /**
     * Begin the volume ramp. Long-running work runs in the supplied [scope].
     */
    fun startVolumeRamp(scope: CoroutineScope)

    /**
     * Cancel any in-flight ramp and restore the user's previous (or last manually-adjusted)
     * volume. Idempotent.
     */
    fun restoreVolume()
}

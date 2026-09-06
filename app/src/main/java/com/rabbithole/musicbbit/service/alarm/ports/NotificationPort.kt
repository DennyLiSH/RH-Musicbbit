package com.rabbithole.musicbbit.service.alarm.ports

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.Song

/**
 * Abstraction over the alarm-specific system notifications shown while an alarm session
 * is active.
 *
 * Production adapter: [com.rabbithole.musicbbit.service.AlarmNotificationHelper].
 * Test adapter: a fake that records the last notification command.
 */
interface NotificationPort {

    /**
     * Show the playing-state alarm notification with playback controls.
     *
     * @param bypassDnd Whether to use the DND-bypass channel. Computed once per fire by
     *   QuietModeBypassResolver (ignoreQuietMode + Notification Policy Access); this
     *   adapter does not re-check the permission.
     */
    fun showAlarmPlaying(alarm: Alarm, song: Song, bypassDnd: Boolean)

    /**
     * Update the existing alarm notification to a paused state.
     */
    fun showAlarmPaused(alarmId: Long)

    /**
     * Cancel the notification associated with the given alarm.
     */
    fun cancel(alarmId: Long)

    /**
     * Show a one-shot error notification when an alarm cannot start playback.
     */
    fun showError(notificationId: Int, title: String, message: String)
}

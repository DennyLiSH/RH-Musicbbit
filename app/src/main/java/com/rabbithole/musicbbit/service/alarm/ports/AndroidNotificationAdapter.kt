package com.rabbithole.musicbbit.service.alarm.ports

import android.content.Context
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.AlarmNotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NotificationPort] adapter that delegates to the existing static
 * [AlarmNotificationHelper]. Behaviour is unchanged — this just provides a seam.
 */
@Singleton
class AndroidNotificationAdapter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationPort {

    override fun showAlarmPlaying(alarm: AlarmEntity, song: Song) {
        AlarmNotificationHelper.show(context, alarm, song)
    }

    override fun showAlarmPaused(alarmId: Long) {
        AlarmNotificationHelper.updatePaused(context, alarmId)
    }

    override fun cancel(alarmId: Long) {
        AlarmNotificationHelper.cancel(context, alarmId)
    }

    override fun showError(notificationId: Int, title: String, message: String) {
        AlarmNotificationHelper.showErrorNotification(context, notificationId, title, message)
    }
}

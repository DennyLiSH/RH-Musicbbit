package com.rabbithole.musicbbit.service

import com.rabbithole.musicbbit.R

data class AlarmNotificationContent(
    val title: String,
    val text: String,
    val bigText: String? = null,
    val isOngoing: Boolean,
    val autoCancel: Boolean,
    val actions: List<Action>,
    val showFullScreenIntent: Boolean = false,
) {
    data class Action(
        val iconResId: Int,
        val label: String,
        val type: ActionType,
    )

    sealed interface ActionType {
        data object Stop : ActionType
        data object Pause : ActionType
        data object Resume : ActionType
        data class ExtendMinutes(val minutes: Int) : ActionType
        data object ExtendToEnd : ActionType
    }
}

class AlarmNotificationContentBuilder(
    private val defaultAlarmLabel: String,
    private val unknownArtist: String,
    private val playingFormat: String,
    private val stop: String,
    private val pause: String,
    private val resume: String,
    private val extend: String,
    private val extendMinutesFormat: String,
    private val toSongEnd: String,
    private val alarmPausedTitle: String,
    private val playbackPausedText: String,
) {

    fun buildPlaying(alarmLabel: String?, songTitle: String, songArtist: String?): AlarmNotificationContent {
        val label = alarmLabel ?: defaultAlarmLabel
        val artist = songArtist ?: unknownArtist
        val fullText = String.format(playingFormat, songTitle, artist)
        return AlarmNotificationContent(
            title = "⏰ $label",
            text = fullText,
            bigText = fullText,
            isOngoing = true,
            autoCancel = false,
            showFullScreenIntent = true,
            actions = listOf(
                AlarmNotificationContent.Action(R.drawable.ic_notification_stop, stop, AlarmNotificationContent.ActionType.Stop),
                AlarmNotificationContent.Action(R.drawable.ic_notification_pause, pause, AlarmNotificationContent.ActionType.Pause),
                AlarmNotificationContent.Action(R.drawable.ic_notification_expand_more, extend, AlarmNotificationContent.ActionType.ExtendMinutes(5)),
                AlarmNotificationContent.Action(R.drawable.ic_notification_snooze, String.format(extendMinutesFormat, 5), AlarmNotificationContent.ActionType.ExtendMinutes(5)),
                AlarmNotificationContent.Action(R.drawable.ic_notification_snooze, String.format(extendMinutesFormat, 10), AlarmNotificationContent.ActionType.ExtendMinutes(10)),
                AlarmNotificationContent.Action(R.drawable.ic_notification_snooze, String.format(extendMinutesFormat, 15), AlarmNotificationContent.ActionType.ExtendMinutes(15)),
                AlarmNotificationContent.Action(R.drawable.ic_notification_skip_next, toSongEnd, AlarmNotificationContent.ActionType.ExtendToEnd),
            ),
        )
    }

    fun buildPaused(): AlarmNotificationContent {
        return AlarmNotificationContent(
            title = "⏰ $alarmPausedTitle",
            text = playbackPausedText,
            isOngoing = true,
            autoCancel = false,
            actions = listOf(
                AlarmNotificationContent.Action(R.drawable.ic_notification_play, resume, AlarmNotificationContent.ActionType.Resume),
                AlarmNotificationContent.Action(R.drawable.ic_notification_stop, stop, AlarmNotificationContent.ActionType.Stop),
            ),
        )
    }

    fun buildError(title: String, message: String): AlarmNotificationContent {
        return AlarmNotificationContent(
            title = "⏰ $title",
            text = message,
            isOngoing = false,
            autoCancel = true,
            actions = emptyList(),
        )
    }
}

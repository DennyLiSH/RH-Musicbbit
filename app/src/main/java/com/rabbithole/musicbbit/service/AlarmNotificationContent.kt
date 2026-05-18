package com.rabbithole.musicbbit.service

import com.rabbithole.musicbbit.R

/**
 * Pure-Kotlin description of an alarm notification's content.
 *
 * Separates "what the notification says and does" from "how it is rendered on Android".
 * This makes notification logic testable without Robolectric.
 */
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

/**
 * Builds [AlarmNotificationContent] from domain models.
 *
 * Pure function — no Android dependencies.
 */
class AlarmNotificationContentBuilder {

    fun buildPlaying(alarmLabel: String?, songTitle: String, songArtist: String?): AlarmNotificationContent {
        val label = alarmLabel ?: "Music Alarm"
        val artist = songArtist ?: "Unknown artist"
        val fullText = "Playing: $songTitle - $artist"
        return AlarmNotificationContent(
            title = "⏰ $label",
            text = fullText,
            bigText = fullText,
            isOngoing = true,
            autoCancel = false,
            showFullScreenIntent = true,
            actions = listOf(
                AlarmNotificationContent.Action(R.drawable.ic_notification_stop, "Stop", AlarmNotificationContent.ActionType.Stop),
                AlarmNotificationContent.Action(R.drawable.ic_notification_pause, "Pause", AlarmNotificationContent.ActionType.Pause),
                AlarmNotificationContent.Action(R.drawable.ic_notification_expand_more, "Extend ▼", AlarmNotificationContent.ActionType.ExtendMinutes(5)),
                AlarmNotificationContent.Action(R.drawable.ic_notification_snooze, "Extend 5 min", AlarmNotificationContent.ActionType.ExtendMinutes(5)),
                AlarmNotificationContent.Action(R.drawable.ic_notification_snooze, "Extend 10 min", AlarmNotificationContent.ActionType.ExtendMinutes(10)),
                AlarmNotificationContent.Action(R.drawable.ic_notification_snooze, "Extend 15 min", AlarmNotificationContent.ActionType.ExtendMinutes(15)),
                AlarmNotificationContent.Action(R.drawable.ic_notification_skip_next, "To song end", AlarmNotificationContent.ActionType.ExtendToEnd),
            ),
        )
    }

    fun buildPaused(): AlarmNotificationContent {
        return AlarmNotificationContent(
            title = "⏰ Alarm Paused",
            text = "Playback has been paused",
            isOngoing = true,
            autoCancel = false,
            actions = listOf(
                AlarmNotificationContent.Action(R.drawable.ic_notification_play, "Resume", AlarmNotificationContent.ActionType.Resume),
                AlarmNotificationContent.Action(R.drawable.ic_notification_stop, "Stop", AlarmNotificationContent.ActionType.Stop),
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

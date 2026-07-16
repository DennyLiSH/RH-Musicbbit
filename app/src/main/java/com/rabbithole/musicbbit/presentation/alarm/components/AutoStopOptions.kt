package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.annotation.StringRes
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.AutoStop

internal sealed interface AutoStopOption {
    data object None : AutoStopOption
    data class Minutes(val value: Int) : AutoStopOption
    data class Songs(val value: Int) : AutoStopOption
}

internal fun AutoStopOption.toAutoStop(): AutoStop? = when (this) {
    is AutoStopOption.Minutes -> AutoStop.ByMinutes(value)
    is AutoStopOption.Songs -> AutoStop.BySongCount(value)
    AutoStopOption.None -> null
}

internal fun AutoStop?.toOption(): AutoStopOption = when (this) {
    is AutoStop.ByMinutes -> AutoStopOption.Minutes(minutes)
    is AutoStop.BySongCount -> AutoStopOption.Songs(count)
    null -> AutoStopOption.None
}

@StringRes
internal fun AutoStopOption.labelRes(): Int = when (this) {
    AutoStopOption.None -> R.string.alarm_edit_auto_stop_none
    is AutoStopOption.Minutes -> when (value) {
        5 -> R.string.alarm_edit_auto_stop_5min
        10 -> R.string.alarm_edit_auto_stop_10min
        15 -> R.string.alarm_edit_auto_stop_15min
        30 -> R.string.alarm_edit_auto_stop_30min
        60 -> R.string.alarm_edit_auto_stop_60min
        else -> R.string.alarm_edit_auto_stop_none
    }
    is AutoStopOption.Songs -> when (value) {
        1 -> R.string.alarm_edit_auto_stop_1song
        2 -> R.string.alarm_edit_auto_stop_2songs
        3 -> R.string.alarm_edit_auto_stop_3songs
        4 -> R.string.alarm_edit_auto_stop_4songs
        5 -> R.string.alarm_edit_auto_stop_5songs
        10 -> R.string.alarm_edit_auto_stop_10songs
        else -> R.string.alarm_edit_auto_stop_none
    }
}

internal val AUTO_STOP_OPTIONS = listOf(
    AutoStopOption.None,
    AutoStopOption.Minutes(5),
    AutoStopOption.Minutes(10),
    AutoStopOption.Minutes(15),
    AutoStopOption.Minutes(30),
    AutoStopOption.Minutes(60),
    AutoStopOption.Songs(1),
    AutoStopOption.Songs(2),
    AutoStopOption.Songs(3),
    AutoStopOption.Songs(4),
    AutoStopOption.Songs(5),
    AutoStopOption.Songs(10),
)

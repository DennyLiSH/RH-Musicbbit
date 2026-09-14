package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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

@Composable
internal fun AutoStopOption.label(): String = when (this) {
    AutoStopOption.None -> stringResource(R.string.alarm_edit_auto_stop_none)
    is AutoStopOption.Minutes -> when (value) {
        5 -> stringResource(R.string.alarm_edit_auto_stop_5min)
        10 -> stringResource(R.string.alarm_edit_auto_stop_10min)
        15 -> stringResource(R.string.alarm_edit_auto_stop_15min)
        30 -> stringResource(R.string.alarm_edit_auto_stop_30min)
        60 -> stringResource(R.string.alarm_edit_auto_stop_60min)
        else -> stringResource(R.string.alarm_edit_auto_stop_minutes_format, value)
    }
    is AutoStopOption.Songs -> when (value) {
        1 -> stringResource(R.string.alarm_edit_auto_stop_1song)
        2 -> stringResource(R.string.alarm_edit_auto_stop_2songs)
        3 -> stringResource(R.string.alarm_edit_auto_stop_3songs)
        4 -> stringResource(R.string.alarm_edit_auto_stop_4songs)
        5 -> stringResource(R.string.alarm_edit_auto_stop_5songs)
        10 -> stringResource(R.string.alarm_edit_auto_stop_10songs)
        else -> stringResource(R.string.alarm_edit_auto_stop_songs_format, value)
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

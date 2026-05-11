package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.data.model.AutoStopConverter
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.toBitmask
import com.rabbithole.musicbbit.domain.model.toDayOfWeekSet

object AlarmMapper {
    fun AlarmEntity.toDomain(): Alarm = Alarm(
        id = id,
        hour = hour,
        minute = minute,
        repeatDays = repeatDaysBitmask.toDayOfWeekSet(),
        excludeHolidays = excludeHolidays,
        playlistId = playlistId,
        isEnabled = isEnabled,
        label = label,
        autoStop = AutoStopConverter.toAutoStop(autoStop),
        lastTriggeredAt = lastTriggeredAt
    )

    fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
        id = id,
        hour = hour,
        minute = minute,
        repeatDaysBitmask = repeatDays.toBitmask(),
        excludeHolidays = excludeHolidays,
        playlistId = playlistId,
        isEnabled = isEnabled,
        label = label,
        autoStop = AutoStopConverter.fromAutoStop(autoStop),
        lastTriggeredAt = lastTriggeredAt
    )
}

package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.local.model.HolidayEntity
import com.rabbithole.musicbbit.domain.model.Holiday

fun HolidayEntity.toDomain(): Holiday = Holiday(
    date = date,
    year = year,
    name = name,
    isHoliday = isHoliday
)

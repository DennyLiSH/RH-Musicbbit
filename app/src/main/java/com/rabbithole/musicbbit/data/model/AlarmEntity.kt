package com.rabbithole.musicbbit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val repeatDaysBitmask: Int,
    val excludeHolidays: Boolean = false,
    val playlistId: Long,
    val isEnabled: Boolean,
    val label: String?,
    val autoStopMinutes: Int?,
    val lastTriggeredAt: Long?
)

package com.rabbithole.musicbbit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.rabbithole.musicbbit.domain.model.AutoStop

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
    val autoStop: String?,
    val lastTriggeredAt: Long?
)

object AutoStopConverter {
    @TypeConverter
    fun fromAutoStop(autoStop: AutoStop?): String? = when (autoStop) {
        null -> null
        is AutoStop.ByMinutes -> "MINUTES:${autoStop.minutes}"
        is AutoStop.BySongCount -> "SONGS:${autoStop.count}"
    }

    @TypeConverter
    fun toAutoStop(value: String?): AutoStop? = when {
        value == null -> null
        value.startsWith("MINUTES:") -> AutoStop.ByMinutes(value.removePrefix("MINUTES:").toInt())
        value.startsWith("SONGS:") -> AutoStop.BySongCount(value.removePrefix("SONGS:").toInt())
        else -> null
    }
}

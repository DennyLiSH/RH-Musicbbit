package com.rabbithole.musicbbit.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for cached Chinese holiday data.
 *
 * @property date ISO date string (YYYY-MM-DD), primary key
 * @property year The year this holiday belongs to
 * @property name Holiday name (e.g., "元旦", "春节")
 * @property isHoliday true = statutory holiday (day off), false = adjusted workday
 * @property fetchedAt Timestamp when this record was fetched from the API
 */
@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey
    val date: String,
    val year: Int,
    val name: String,
    val isHoliday: Boolean,
    val fetchedAt: Long
)

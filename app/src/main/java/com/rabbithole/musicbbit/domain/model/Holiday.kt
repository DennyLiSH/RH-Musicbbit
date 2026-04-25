package com.rabbithole.musicbbit.domain.model

/**
 * Domain model representing a Chinese holiday or adjusted workday.
 *
 * @property date ISO date string (YYYY-MM-DD)
 * @property year The year
 * @property name Holiday name (e.g., "元旦", "春节")
 * @property isHoliday true = statutory holiday (day off), false = adjusted workday
 */
data class Holiday(
    val date: String,
    val year: Int,
    val name: String,
    val isHoliday: Boolean
)

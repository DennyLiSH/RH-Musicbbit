package com.rabbithole.musicbbit.domain.model

/**
 * Domain model representing a Chinese holiday or adjusted workday.
 *
 * This model intentionally excludes cache metadata (e.g. `fetchedAt`),
 * which is managed by the data layer. The domain concern is only
 * "what holiday is on a given date", not when it was fetched from the API.
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

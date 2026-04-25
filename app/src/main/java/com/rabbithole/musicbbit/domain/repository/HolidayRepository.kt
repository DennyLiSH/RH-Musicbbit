package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.Holiday
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Chinese holiday data.
 *
 * Provides both cached local data and remote refresh capability.
 */
interface HolidayRepository {

    /**
     * Flow of cached holidays for a given year.
     */
    fun getHolidaysForYear(year: Int): Flow<List<Holiday>>

    /**
     * Force refresh holiday data from remote API.
     *
     * @param year The year to refresh
     * @return Result indicating success or failure
     */
    suspend fun refreshHolidays(year: Int): Result<Unit>

    /**
     * Check if the given date is a workday, considering holidays and adjusted workdays.
     *
     * @param date ISO date string (YYYY-MM-DD)
     * @return true if the date is a workday
     */
    suspend fun isWorkday(date: String): Boolean
}

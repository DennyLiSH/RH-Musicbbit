package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.Holiday
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Chinese holiday data.
 *
 * Provides both cached local data and remote refresh capability.
 *
 * Error contract: `maybeRefreshHolidays` returns [Result] — callers decide whether
 * to fall back to cached data; `isWorkday` parse failures throw explicitly per
 * ADR 0007 (prefer errors over silent degradation).
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
     * @param date ISO date string (YYYY-MM-DD). Callers must ensure [date] is well-formed
     *   (e.g., produced by `NextOccurrenceCalculator.formatDate` or equivalent ISO validation).
     * @return true if the date is a workday
     * @throws IllegalStateException if [date] is not a parseable ISO date. Per ADR 0007
     *   (prefer errors over silent degradation), parse failures surface explicitly
     *   rather than defaulting to a workday value.
     */
    suspend fun isWorkday(date: String): Boolean

    /**
     * Refresh holiday data from the API at most once per calendar month.
     * Callers that need up-to-date holiday data should invoke this before calling [isWorkday].
     *
     * @return Result indicating success or failure. Failures (network, API, DataStore)
     *   are surfaced explicitly so callers can decide whether to fall back to cached
     *   data, log a warning, or escalate. `isWorkday` continues to work via its
     *   own Room → assets fallback chain regardless of this result.
     */
    suspend fun maybeRefreshHolidays(year: Int): Result<Unit>
}

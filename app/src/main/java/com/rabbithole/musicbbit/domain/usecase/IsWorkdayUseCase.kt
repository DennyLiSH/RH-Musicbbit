package com.rabbithole.musicbbit.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.rabbithole.musicbbit.data.local.datastore.SettingsKeys
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Use case to check if a given date is a workday.
 *
 * Considers both the day of week and Chinese holiday data.
 * Automatically refreshes holiday data from the API once per month.
 */
class IsWorkdayUseCase @Inject constructor(
    private val holidayRepository: HolidayRepository,
    private val refreshHolidaysUseCase: RefreshHolidaysUseCase,
    private val dataStore: DataStore<Preferences>
) {
    /**
     * Check if the given date is a workday.
     *
     * @param date The date to check
     * @return true if the date is a workday
     */
    suspend operator fun invoke(date: LocalDate): Boolean {
        maybeRefreshHolidays(date.year)
        return holidayRepository.isWorkday(date.toString())
    }

    /**
     * Refresh holiday data at most once per month.
     *
     * Compares the current month with the last recorded API call month in DataStore.
     * If they differ (or no record exists), triggers a refresh for the given year.
     */
    private suspend fun maybeRefreshHolidays(year: Int) {
        try {
            val currentMonth = YearMonth.now().toString() // "YYYY-MM"
            val preferences = dataStore.data.first()
            val lastCallMonth = preferences[SettingsKeys.LAST_HOLIDAY_API_CALL_MONTH]

            if (lastCallMonth == currentMonth) {
                Timber.d("Holiday API already called this month ($currentMonth), skipping refresh")
                return
            }

            Timber.i("Triggering holiday refresh for year $year (last call: $lastCallMonth, current: $currentMonth)")
            val result = refreshHolidaysUseCase(year)

            result.onSuccess {
                dataStore.edit { prefs ->
                    prefs[SettingsKeys.LAST_HOLIDAY_API_CALL_MONTH] = currentMonth
                }
                Timber.i("Holiday refresh succeeded for year $year, recorded month $currentMonth")
            }.onFailure { error ->
                Timber.w(error, "Holiday refresh failed for year $year, will retry next month")
            }
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during holiday refresh check")
        }
    }
}

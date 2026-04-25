package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import javax.inject.Inject

/**
 * Use case to refresh holiday data from the remote API.
 */
class RefreshHolidaysUseCase @Inject constructor(
    private val holidayRepository: HolidayRepository
) {
    /**
     * Refresh holiday data for the given year.
     *
     * @param year The year to refresh (defaults to current year)
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(year: Int): Result<Unit> {
        return holidayRepository.refreshHolidays(year)
    }
}

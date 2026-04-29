package com.rabbithole.musicbbit.domain.usecase

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.rabbithole.musicbbit.data.local.datastore.SettingsKeys
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class IsWorkdayUseCaseTest {

    private val holidayRepository: HolidayRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testDataStore = PreferenceDataStoreFactory.createForTesting(scope = testScope)
    private lateinit var useCase: IsWorkdayUseCase

    @Before
    fun setup() {
        useCase = IsWorkdayUseCase(holidayRepository, testDataStore)
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `delegates to holidayRepository isWorkday`() = runTest(testDispatcher) {
        // Set current month so no refresh is triggered
        val currentMonth = YearMonth.now().toString()
        testDataStore.edit { it[SettingsKeys.LAST_HOLIDAY_API_CALL_MONTH] = currentMonth }

        val date = LocalDate.of(2026, 1, 5) // Monday
        coEvery { holidayRepository.isWorkday("2026-01-05") } returns true

        val result = useCase.invoke(date)

        assertTrue(result)
        coVerify { holidayRepository.isWorkday("2026-01-05") }
    }

    @Test
    fun `triggers refresh on first call`() = runTest(testDispatcher) {
        // No LAST_HOLIDAY_API_CALL_MONTH stored → triggers refresh
        coEvery { holidayRepository.refreshHolidays(2026) } returns Result.success(Unit)
        coEvery { holidayRepository.isWorkday(any()) } returns true

        useCase.invoke(LocalDate.of(2026, 1, 5))

        coVerify { holidayRepository.refreshHolidays(2026) }
    }

    @Test
    fun `skips refresh when already called this month`() = runTest(testDispatcher) {
        val currentMonth = YearMonth.now().toString()
        testDataStore.edit { it[SettingsKeys.LAST_HOLIDAY_API_CALL_MONTH] = currentMonth }
        coEvery { holidayRepository.isWorkday(any()) } returns true

        useCase.invoke(LocalDate.of(2026, 1, 5))

        coVerify(exactly = 0) { holidayRepository.refreshHolidays(any()) }
    }

    @Test
    fun `refresh failure does not prevent workday check`() = runTest(testDispatcher) {
        // No stored month → triggers refresh, but refresh fails
        coEvery { holidayRepository.refreshHolidays(2026) } returns Result.failure(RuntimeException("network"))
        coEvery { holidayRepository.isWorkday("2026-01-05") } returns true

        val result = useCase.invoke(LocalDate.of(2026, 1, 5))

        assertTrue(result)
        coVerify { holidayRepository.isWorkday("2026-01-05") }
    }
}

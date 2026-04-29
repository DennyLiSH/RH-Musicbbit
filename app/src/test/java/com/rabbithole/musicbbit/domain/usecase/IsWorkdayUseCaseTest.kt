package com.rabbithole.musicbbit.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rabbithole.musicbbit.data.local.datastore.SettingsKeys
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val lastMonthKey = stringPreferencesKey("last_holiday_api_month")

    private val prefsMap = mutableMapOf<String, Any?>(
        "last_holiday_api_month" to null,
    )

    private val prefsFlow = MutableStateFlow(createMockPrefs())

    private fun createMockPrefs(): Preferences {
        val prefs = mockk<Preferences>(relaxed = true)
        every { prefs[lastMonthKey] } answers { prefsMap["last_holiday_api_month"] as? String }
        every { prefs.get(lastMonthKey) } answers { prefsMap["last_holiday_api_month"] as? String }
        return prefs
    }

    private val dataStore: DataStore<Preferences> = mockk(relaxed = true) {
        every { data } returns prefsFlow
    }

    private lateinit var useCase: IsWorkdayUseCase

    @Before
    fun setup() {
        useCase = IsWorkdayUseCase(holidayRepository, dataStore)
    }

    @Test
    fun `delegates to holidayRepository isWorkday`() = runTest(testDispatcher) {
        // Set current month so no refresh is triggered
        prefsMap["last_holiday_api_month"] = YearMonth.now().toString()
        prefsFlow.value = createMockPrefs()

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
        prefsMap["last_holiday_api_month"] = YearMonth.now().toString()
        prefsFlow.value = createMockPrefs()
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

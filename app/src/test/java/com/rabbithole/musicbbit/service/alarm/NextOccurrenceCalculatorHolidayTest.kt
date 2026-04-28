package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.usecase.IsWorkdayUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import timber.log.Timber
import java.util.Calendar
import java.util.TimeZone

/**
 * Holiday-aware unit tests for [NextOccurrenceCalculator.nextOccurrence].
 *
 * These tests verify that the calculator correctly skips statutory holidays,
 * honours adjusted workdays (make-up shifts on weekends), and handles DST transitions.
 *
 * Mocking pattern follows [NextOccurrenceCalculatorProductionTest]:
 *   - [IsWorkdayUseCase] is mocked with [coEvery] to control workday/holiday behaviour.
 *   - [Clock] is mocked with [every] to pin "now".
 */
@RunWith(JUnit4::class)
class NextOccurrenceCalculatorHolidayTest {

    init {
        // Ensure Timber is planted so production code logging doesn't crash
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun fixedNow(
        year: Int = 2024,
        month: Int = Calendar.JANUARY,
        day: Int = 15,
        hour: Int = 10,
        minute: Int = 0,
        timeZone: TimeZone = TimeZone.getTimeZone("America/New_York"),
    ): Calendar = Calendar.getInstance(timeZone).apply {
        set(year, month, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun createCalculator(
        isWorkdayResult: Boolean = true,
        nonWorkdayDates: List<String> = emptyList(),
        now: Calendar = fixedNow(),
    ): NextOccurrenceCalculator {
        val isWorkdayUseCase = mockk<IsWorkdayUseCase>()
        coEvery { isWorkdayUseCase(any()) } answers {
            val date = firstArg<java.time.LocalDate>()
            date.toString() !in nonWorkdayDates
        }

        val clock = mockk<Clock>()
        every { clock.nowMs() } returns now.timeInMillis

        return NextOccurrenceCalculator(isWorkdayUseCase, clock)
    }

    @Test
    fun `nextOccurrence - daily excluding holidays skips holiday to next workday`() = runTest {
        // Jan 15 2024 is Monday. Simulate it being a holiday.
        val now = fixedNow(day = 15, hour = 8)
        val calculator = createCalculator(nonWorkdayDates = listOf("2024-01-15"), now = now)

        // Daily mode (all days) with excludeHolidays=true should skip the holiday
        val result = calculator.nextOccurrence(10, 0, 0b1111111, excludeHolidays = true)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrence - excluding holidays honours adjusted workday on Saturday`() = runTest {
        // Jan 13 2024 is Saturday. Simulate it being an adjusted workday (make-up shift).
        val now = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 13, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // isWorkdayUseCase returns true for all dates (including Saturday)
        val calculator = createCalculator(isWorkdayResult = true, now = now)

        // With excludeHolidays=true, Saturday as workday should ring
        val result = calculator.nextOccurrence(10, 0, 0b1111111, excludeHolidays = true)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrence - DST spring forward alarm at 2_30 AM`() = runTest {
        // Mar 10 2024: DST starts at 2:00 AM, clocks jump to 3:00 AM.
        // Alarm at 2:30 AM should resolve to 3:30 AM local time.
        val tz = TimeZone.getTimeZone("America/New_York")
        val now = Calendar.getInstance(tz).apply {
            // Fixed to 1:00 AM on DST transition day
            set(2024, Calendar.MARCH, 10, 1, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calculator = createCalculator(now = now)

        val result = calculator.nextOccurrence(2, 30, 0b1111111, excludeHolidays = false)

        // At 1:00 AM, the 2:30 AM alarm time is in the future.
        // Because DST springs forward, 2:30 AM local doesn't exist.
        // Calendar will resolve it to 3:30 AM local (which is 2:30 AM UTC-4).
        val expected = Calendar.getInstance(tz).apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrence - DST fall back alarm at 2_30 AM`() = runTest {
        // Nov 3 2024: DST ends at 2:00 AM, clocks fall back to 1:00 AM.
        // Alarm at 2:30 AM should resolve to 2:30 AM standard time (UTC-5).
        val tz = TimeZone.getTimeZone("America/New_York")
        val now = Calendar.getInstance(tz).apply {
            // Fixed to 1:00 AM on fall-back day
            set(2024, Calendar.NOVEMBER, 3, 1, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calculator = createCalculator(now = now)

        val result = calculator.nextOccurrence(2, 30, 0b1111111, excludeHolidays = false)

        // At 1:00 AM, the 2:30 AM alarm time is in the future.
        // After fall back, 2:30 AM is standard time (UTC-5).
        val expected = Calendar.getInstance(tz).apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrence - weekdays mode on Monday holiday skips to Tuesday`() = runTest {
        // Jan 15 2024 is Monday (holiday). Weekdays bitmask = Mon-Fri = 0b0011111.
        val now = fixedNow(day = 15, hour = 8)
        val calculator = createCalculator(nonWorkdayDates = listOf("2024-01-15"), now = now)

        val result = calculator.nextOccurrence(10, 0, 0b0011111, excludeHolidays = false)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrence - daily no-exclude rings even on holiday`() = runTest {
        // Jan 15 2024 is Monday (holiday). excludeHolidays=false, everyday mode.
        val now = fixedNow(day = 15, hour = 8)
        val calculator = createCalculator(nonWorkdayDates = listOf("2024-01-15"), now = now)

        val result = calculator.nextOccurrence(10, 0, 0b1111111, excludeHolidays = false)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }
}

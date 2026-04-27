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
import java.util.Calendar

@RunWith(JUnit4::class)
class NextOccurrenceCalculatorProductionTest {

    private fun fixedNow(
        year: Int = 2024,
        month: Int = Calendar.JANUARY,
        day: Int = 15,  // Monday
        hour: Int = 10,
        minute: Int = 0
    ): Calendar = Calendar.getInstance().apply {
        set(year, month, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun createCalculator(
        isWorkdayResult: Boolean = true,
        now: Calendar = fixedNow()
    ): NextOccurrenceCalculator {
        val isWorkdayUseCase = mockk<IsWorkdayUseCase>()
        coEvery { isWorkdayUseCase(any()) } returns isWorkdayResult

        val clock = mockk<Clock>()
        every { clock.nowMs() } returns now.timeInMillis

        return NextOccurrenceCalculator(isWorkdayUseCase, clock)
    }

    @Test
    fun `nextOccurrence - daily mode with excludeHolidays=false rings on holiday`() = runTest {
        val now = fixedNow(day = 15, hour = 8)  // Monday
        val calculator = createCalculator(isWorkdayResult = false, now = now)

        // Daily mode (all days, excludeHolidays=false) should ring even on non-workday
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

    @Test
    fun `nextOccurrence - excludingHolidays mode skips holiday`() = runTest {
        val now = fixedNow(day = 15, hour = 8)  // Monday
        val calculator = createCalculator(isWorkdayResult = false, now = now)

        // Excluding holidays (all days, excludeHolidays=true) should skip non-workday
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
    fun `nextOccurrence - excludingHolidays mode honours adjusted workday`() = runTest {
        // Jan 13 2024 is Saturday. Simulate it being an adjusted workday.
        val now = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 13, 8, 0, 0)  // Saturday
            set(Calendar.MILLISECOND, 0)
        }
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
    fun `nextOccurrence - weekdays mode skips holiday`() = runTest {
        val now = fixedNow(day = 15, hour = 8)  // Monday
        val calculator = createCalculator(isWorkdayResult = false, now = now)

        // Weekdays mode (Mon-Fri, bitmask=0b0011111) on a holiday should skip
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
}

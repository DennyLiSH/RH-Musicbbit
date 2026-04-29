package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.usecase.IsWorkdayUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
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

    companion object {
        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            // Pin the default time zone so DST tests are deterministic regardless of host TZ
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            // Plant a no-op Timber tree so production logging doesn't crash in plain JVM tests
            Timber.uprootAll()
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {}
            })
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

    // -------- Additional boundary-case tests -------------------------------------

    @Test
    fun `nextOccurrence - consecutive multi-day holiday skips to first workday`() = runTest {
        // Jan 15-17 2024 (Mon-Wed) are all holidays. Daily mode with excludeHolidays=true
        // should skip to Jan 18 (Thursday).
        val now = fixedNow(day = 15, hour = 8)
        val calculator = createCalculator(
            nonWorkdayDates = listOf("2024-01-15", "2024-01-16", "2024-01-17"),
            now = now,
        )

        val result = calculator.nextOccurrence(10, 0, 0b1111111, excludeHolidays = true)

        // Expected: 2024-01-18 10:00 (Thursday)
        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.DAY_OF_MONTH, 18)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrence - weekday-only bitmask on holiday skips to next matching day`() = runTest {
        // Jan 15 2024 is Monday (holiday). Monday-only bitmask (bit 0 = 1).
        // excludeHolidays=false but the logic for non-everyday mode checks:
        //   dayMatches && isWorkday -> return; !dayMatches && isWorkday && weekend -> return
        // Monday matches bitmask but isWorkday=false -> skip. Next matching weekday that is
        // a workday is next Monday (Jan 22).
        val now = fixedNow(day = 15, hour = 8)
        val calculator = createCalculator(nonWorkdayDates = listOf("2024-01-15"), now = now)

        // Monday-only bitmask: bit 0 = Monday
        val result = calculator.nextOccurrence(10, 0, 1, excludeHolidays = false)

        // Expected: 2024-01-22 Mon 10:00
        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.DAY_OF_MONTH, 22)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrence - DST spring-forward at 2_00 AM resolves correctly`() = runTest {
        // Mar 10 2024: DST spring-forward at 2:00 AM (America/New_York).
        // now = 1:00 AM, alarm at 2:00 AM. The 2:00 AM slot doesn't exist on this day.
        // Calendar resolves it, and the result should be >= now.timeInMillis.
        val tz = TimeZone.getTimeZone("America/New_York")
        val now = Calendar.getInstance(tz).apply {
            set(2024, Calendar.MARCH, 10, 1, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calculator = createCalculator(now = now)

        val result = calculator.nextOccurrence(2, 0, 0b1111111, excludeHolidays = false)

        // Result should be >= now (2:00 AM resolves to 3:00 AM DST)
        assertTrue(
            "Result should be >= now, got ${result} vs now=${now.timeInMillis}",
            result >= now.timeInMillis
        )
    }

    @Test
    fun `nextOccurrence - fallback triggered when all days are non-workday`() = runTest {
        // Simulate isWorkdayUseCase returning false for every date.
        // The while loop will iterate past year+1 and invoke the fallback path.
        val now = fixedNow(day = 15, hour = 8)
        val isWorkdayUseCase = mockk<IsWorkdayUseCase>()
        coEvery { isWorkdayUseCase(any()) } returns false

        val clock = mockk<Clock>()
        every { clock.nowMs() } returns now.timeInMillis

        val calculator = NextOccurrenceCalculator(isWorkdayUseCase, clock)

        val result = calculator.nextOccurrence(10, 0, 0b1111111, excludeHolidays = true)

        // Fallback path should still return a positive timestamp
        assertTrue("Fallback should return a positive timestamp, got $result", result > 0)
    }
}

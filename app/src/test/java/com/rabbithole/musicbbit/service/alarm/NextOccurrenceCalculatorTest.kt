package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import java.time.DayOfWeek
import java.util.Calendar

/**
 * Unit tests for [NextOccurrenceCalculator.nextOccurrence].
 *
 * The previous Companion.nextOccurrenceFallback path was removed (ADR 0007 follow-up:
 * prefer errors over silent degradation). Tests now cover:
 *  - happy paths via the production suspend entrypoint with a mocked HolidayRepository
 *  - boundary cases (year transition, isWorkday exception propagation, hard-error path)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class NextOccurrenceCalculatorTest {

    private val holidayRepository: HolidayRepository = mock {
        onBlocking { isWorkday(any<String>()) } doReturn true
        onBlocking { maybeRefreshHolidays(any<Int>()) } doReturn Result.success(Unit)
    }
    private val clock: Clock = mock {
        on { nowMs() } doReturn fixedNow(2024, Calendar.JANUARY, 15, 10, 0).timeInMillis
    }
    private val calculator = NextOccurrenceCalculator(holidayRepository, clock)

    @Test
    fun `nextOccurrence - one-time alarm in future returns same day`() = runTest {
        wheneverBlocking { holidayRepository.maybeRefreshHolidays(2024) } doReturn Result.success(Unit)

        val result = calculator.nextOccurrence(14, 0, emptySet())

        val expected = fixedNow(2024, Calendar.JANUARY, 15, 14, 0).timeInMillis
        assertEquals(expected, result)
    }

    @Test
    fun `nextOccurrence - one-time alarm in past returns next day`() = runTest {
        wheneverBlocking { holidayRepository.maybeRefreshHolidays(2024) } doReturn Result.success(Unit)

        val result = calculator.nextOccurrence(8, 0, emptySet())

        val expected = fixedNow(2024, Calendar.JANUARY, 16, 8, 0).timeInMillis
        assertEquals(expected, result)
    }

    @Test
    fun `nextOccurrence - repeating alarm skips to next matching day`() = runTest {
        // 2024-01-15 is Monday; alarm set for Wednesday at 8:00, current time Mon 10:00
        wheneverBlocking { holidayRepository.maybeRefreshHolidays(2024) } doReturn Result.success(Unit)

        val result = calculator.nextOccurrence(8, 0, setOf(DayOfWeek.WEDNESDAY))

        val expected = fixedNow(2024, Calendar.JANUARY, 17, 8, 0).timeInMillis
        assertEquals(expected, result)
    }

    @Test
    fun `nextOccurrence - repeating alarm wraps around to next week`() = runTest {
        // 2024-01-19 is Friday; alarm set for Monday, current time Fri 15:00
        whenever { clock.nowMs() } doReturn fixedNow(2024, Calendar.JANUARY, 19, 15, 0).timeInMillis
        wheneverBlocking { holidayRepository.maybeRefreshHolidays(2024) } doReturn Result.success(Unit)

        val result = calculator.nextOccurrence(10, 0, setOf(DayOfWeek.MONDAY))

        // Next Monday is 2024-01-22
        val expected = fixedNow(2024, Calendar.JANUARY, 22, 10, 0).timeInMillis
        assertEquals(expected, result)
    }

    @Test
    fun `nextOccurrence - exclude holidays skips non-workday selected days`() = runTest {
        // Monday 2024-01-15 at 10:00, alarm set Mon/Wed/Fri with excludeHolidays=true
        // Treat every day as non-workday → search window exceeds 2y → throws
        wheneverBlocking { holidayRepository.maybeRefreshHolidays(2024) } doReturn Result.success(Unit)
        wheneverBlocking { holidayRepository.isWorkday(any<String>()) } doReturn false

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                calculator.nextOccurrence(8, 0, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), excludeHolidays = true)
            }
        }
    }

    @Test
    fun `nextOccurrence - propagates isWorkday IOException`() = runTest {
        wheneverBlocking { holidayRepository.maybeRefreshHolidays(2024) } doReturn Result.success(Unit)
        wheneverBlocking { holidayRepository.isWorkday(any<String>()) } doThrow RuntimeException("DAO down")

        assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking {
                calculator.nextOccurrence(8, 0, setOf(DayOfWeek.MONDAY), excludeHolidays = true)
            }
        }
    }

    @Test
    fun `nextOccurrence - handles year boundary`() = runTest {
        // 2027-12-30, alarm for 9:00 every Monday; should find next Monday in 2028
        whenever { clock.nowMs() } doReturn fixedNow(2027, Calendar.DECEMBER, 30, 10, 0).timeInMillis
        wheneverBlocking { holidayRepository.maybeRefreshHolidays(2027) } doReturn Result.success(Unit)
        wheneverBlocking { holidayRepository.maybeRefreshHolidays(2028) } doReturn Result.success(Unit)
        wheneverBlocking { holidayRepository.isWorkday(any<String>()) } doReturn true

        val result = calculator.nextOccurrence(9, 0, setOf(DayOfWeek.MONDAY))

        // 2027-12-30 is Thursday; next Monday is 2028-01-03
        val cal = Calendar.getInstance().apply {
            set(2028, Calendar.JANUARY, 3, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(cal.timeInMillis, result)
    }

    @Test
    fun `nextOccurrence - throws when maybeRefreshHolidays succeeds but isWorkday always false`() = runTest {
        // Covers the assets fallback chain failure: refresh ok but every day is non-workday.
        wheneverBlocking { holidayRepository.maybeRefreshHolidays(any<Int>()) } doReturn Result.success(Unit)
        wheneverBlocking { holidayRepository.isWorkday(any<String>()) } doReturn false

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                calculator.nextOccurrence(8, 0, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), excludeHolidays = true)
            }
        }
    }

    @Test
    fun `nextOccurrence - result is always in the future`() = runTest {
        whenever { clock.nowMs() } doReturn fixedNow(2024, Calendar.JANUARY, 15, 12, 30).timeInMillis
        wheneverBlocking { holidayRepository.maybeRefreshHolidays(any<Int>()) } doReturn Result.success(Unit)
        wheneverBlocking { holidayRepository.isWorkday(any<String>()) } doReturn true

        val alternatingDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)
        val result = calculator.nextOccurrence(9, 30, alternatingDays)

        assertTrue("Trigger time must be >= now", result >= clock.nowMs())
    }

    private fun fixedNow(
        year: Int = 2024,
        month: Int = Calendar.JANUARY,
        day: Int = 15,
        hour: Int = 10,
        minute: Int = 0
    ): Calendar = Calendar.getInstance().apply {
        set(year, month, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

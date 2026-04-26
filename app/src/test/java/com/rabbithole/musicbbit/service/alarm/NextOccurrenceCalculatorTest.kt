package com.rabbithole.musicbbit.service.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Calendar

/**
 * Unit tests for [NextOccurrenceCalculator.nextOccurrenceFallback].
 *
 * The fallback path is the pure-Kotlin calendar math used when holiday data is unavailable
 * or when a hard search cap is reached. It is the seam previously exposed as
 * AlarmScheduler.calculateNextTriggerTime.
 *
 * All tests use the test-visible overload that accepts a fixed "now" [Calendar]
 * so results are deterministic.
 */
@RunWith(JUnit4::class)
class NextOccurrenceCalculatorTest {

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

    @Test
    fun `nextOccurrenceFallback - one-time alarm in future returns same day`() {
        val now = fixedNow(hour = 10, minute = 0)

        val result = NextOccurrenceCalculator.nextOccurrenceFallback(14, 0, 0, now)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrenceFallback - one-time alarm in past returns next day`() {
        val now = fixedNow(hour = 15, minute = 0)

        val result = NextOccurrenceCalculator.nextOccurrenceFallback(10, 0, 0, now)

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
    fun `nextOccurrenceFallback - one-time alarm at exact same time returns same day`() {
        val now = fixedNow(hour = 10, minute = 0)

        val result = NextOccurrenceCalculator.nextOccurrenceFallback(10, 0, 0, now)

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
    fun `nextOccurrenceFallback - one-time alarm one minute in past returns next day`() {
        val now = fixedNow(hour = 10, minute = 1)

        val result = NextOccurrenceCalculator.nextOccurrenceFallback(10, 0, 0, now)

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
    fun `nextOccurrenceFallback - repeating alarm all days finds today if future`() {
        val now = fixedNow(hour = 8, minute = 0)

        val result = NextOccurrenceCalculator.nextOccurrenceFallback(10, 0, 0b1111111, now)

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
    fun `nextOccurrenceFallback - repeating alarm all days finds tomorrow if past`() {
        val now = fixedNow(hour = 15, minute = 0)

        val result = NextOccurrenceCalculator.nextOccurrenceFallback(10, 0, 0b1111111, now)

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
    fun `nextOccurrenceFallback - repeating alarm skips to next matching day`() {
        val now = fixedNow(hour = 10, minute = 0)
        val wednesdayOnly = 1 shl 2

        val result = NextOccurrenceCalculator.nextOccurrenceFallback(8, 0, wednesdayOnly, now)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_MONTH, 2)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrenceFallback - repeating alarm wraps around to next week`() {
        val now = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 19, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val mondayOnly = 1 shl 0

        val result = NextOccurrenceCalculator.nextOccurrenceFallback(10, 0, mondayOnly, now)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_MONTH, 3)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrenceFallback - repeating alarm same day selected but past time finds next week`() {
        val now = fixedNow(hour = 15, minute = 0)
        val mondayOnly = 1 shl 0

        val result = NextOccurrenceCalculator.nextOccurrenceFallback(10, 0, mondayOnly, now)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_MONTH, 7)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrenceFallback - repeating alarm same day selected and future time returns today`() {
        val now = fixedNow(hour = 8, minute = 0)
        val mondayOnly = 1 shl 0

        val result = NextOccurrenceCalculator.nextOccurrenceFallback(10, 0, mondayOnly, now)

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
    fun `nextOccurrenceFallback - weekend only skips weekdays`() {
        val now = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 17, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val weekendMask = (1 shl 5) or (1 shl 6)

        val result = NextOccurrenceCalculator.nextOccurrenceFallback(8, 0, weekendMask, now)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_MONTH, 3)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `nextOccurrenceFallback - result is always in the future`() {
        val now = fixedNow(hour = 12, minute = 30)
        val result = NextOccurrenceCalculator.nextOccurrenceFallback(9, 30, 0b1010101, now)
        assertTrue("Trigger time must be >= now", result >= now.timeInMillis)
    }
}

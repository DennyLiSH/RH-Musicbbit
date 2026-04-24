package com.rabbithole.musicbbit.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Calendar

/**
 * Unit tests for [AlarmScheduler.calculateNextTriggerTime].
 *
 * Tests cover one-time alarms (future / past / exact-now), repeating alarms
 * with various day bitmasks, and edge cases such as wrap-around and single-day repeats.
 *
 * All tests use the test-visible overload that accepts a fixed "now" [Calendar]
 * so results are deterministic.
 */
@RunWith(JUnit4::class)
class AlarmSchedulerTest {

    // ------------------------------------------------------------------
    // Helper: create a fixed "now" calendar
    // ------------------------------------------------------------------
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

    // ------------------------------------------------------------------
    // One-time alarms (repeatDaysBitmask == 0)
    // ------------------------------------------------------------------

    @Test
    fun `calculateNextTriggerTime - one-time alarm in future returns same day`() {
        // Current time: 2024-01-15 10:00, Alarm time: 14:00
        val now = fixedNow(hour = 10, minute = 0)

        val result = AlarmScheduler.calculateNextTriggerTime(14, 0, 0, now)

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
    fun `calculateNextTriggerTime - one-time alarm in past returns next day`() {
        // Current time: 2024-01-15 15:00, Alarm time: 10:00
        val now = fixedNow(hour = 15, minute = 0)

        val result = AlarmScheduler.calculateNextTriggerTime(10, 0, 0, now)

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
    fun `calculateNextTriggerTime - one-time alarm at exact same time returns same day`() {
        // Current time: 2024-01-15 10:00, Alarm time: 10:00
        // candidate.before(now) is false when equal, so it stays on same day
        val now = fixedNow(hour = 10, minute = 0)

        val result = AlarmScheduler.calculateNextTriggerTime(10, 0, 0, now)

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
    fun `calculateNextTriggerTime - one-time alarm one minute in past returns next day`() {
        // Current time: 2024-01-15 10:01, Alarm time: 10:00
        val now = fixedNow(hour = 10, minute = 1)

        val result = AlarmScheduler.calculateNextTriggerTime(10, 0, 0, now)

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

    // ------------------------------------------------------------------
    // Repeating alarms (repeatDaysBitmask != 0)
    // ------------------------------------------------------------------

    @Test
    fun `calculateNextTriggerTime - repeating alarm all days finds today if future`() {
        // Monday 2024-01-15 08:00, alarm at 10:00, all days selected
        val now = fixedNow(hour = 8, minute = 0)

        val result = AlarmScheduler.calculateNextTriggerTime(10, 0, 0b1111111, now)

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
    fun `calculateNextTriggerTime - repeating alarm all days finds tomorrow if past`() {
        // Monday 2024-01-15 15:00, alarm at 10:00, all days selected
        val now = fixedNow(hour = 15, minute = 0)

        val result = AlarmScheduler.calculateNextTriggerTime(10, 0, 0b1111111, now)

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
    fun `calculateNextTriggerTime - repeating alarm skips to next matching day`() {
        // Monday 2024-01-15 10:00, alarm at 08:00, only Wednesday (bit2) selected
        // Today is Monday, so next Wednesday is 2024-01-17
        val now = fixedNow(hour = 10, minute = 0)
        val wednesdayOnly = 1 shl 2 // bit2 = Wednesday

        val result = AlarmScheduler.calculateNextTriggerTime(8, 0, wednesdayOnly, now)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            // Monday -> Wednesday is +2 days
            add(Calendar.DAY_OF_MONTH, 2)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `calculateNextTriggerTime - repeating alarm wraps around to next week`() {
        // Friday 2024-01-19 15:00, alarm at 10:00, only Monday (bit0) selected
        // Next Monday is 2024-01-22 (3 days later)
        val now = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 19, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val mondayOnly = 1 shl 0 // bit0 = Monday

        val result = AlarmScheduler.calculateNextTriggerTime(10, 0, mondayOnly, now)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_MONTH, 3) // Friday -> Monday
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `calculateNextTriggerTime - repeating alarm same day selected but past time finds next week`() {
        // Monday 2024-01-15 15:00, alarm at 10:00, only Monday selected
        // Time has passed today, so must go to next Monday (7 days later)
        val now = fixedNow(hour = 15, minute = 0)
        val mondayOnly = 1 shl 0 // bit0 = Monday

        val result = AlarmScheduler.calculateNextTriggerTime(10, 0, mondayOnly, now)

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
    fun `calculateNextTriggerTime - repeating alarm same day selected and future time returns today`() {
        // Monday 2024-01-15 08:00, alarm at 10:00, only Monday selected
        val now = fixedNow(hour = 8, minute = 0)
        val mondayOnly = 1 shl 0 // bit0 = Monday

        val result = AlarmScheduler.calculateNextTriggerTime(10, 0, mondayOnly, now)

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
    fun `calculateNextTriggerTime - weekend only skips weekdays`() {
        // Wednesday 2024-01-17 10:00, alarm at 08:00, Saturday+Sunday selected
        // Next match is Saturday 2024-01-20 (+3 days)
        val now = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 17, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val weekendMask = (1 shl 5) or (1 shl 6) // Saturday + Sunday

        val result = AlarmScheduler.calculateNextTriggerTime(8, 0, weekendMask, now)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_MONTH, 3) // Wed -> Sat
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `calculateNextTriggerTime - result is always in the future`() {
        // Random configuration: verify result >= now
        val now = fixedNow(hour = 12, minute = 30)
        val result = AlarmScheduler.calculateNextTriggerTime(9, 30, 0b1010101, now)
        assertTrue("Trigger time must be >= now", result >= now.timeInMillis)
    }
}

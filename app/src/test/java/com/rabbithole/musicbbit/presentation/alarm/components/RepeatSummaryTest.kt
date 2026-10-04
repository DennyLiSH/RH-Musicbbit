package com.rabbithole.musicbbit.presentation.alarm.components

import com.rabbithole.musicbbit.R
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class RepeatSummaryTest {

    @Test
    fun `empty days is one-time`() {
        assertEquals(RepeatSummary.OneTime, repeatSummary(emptySet(), excludeHolidays = false))
    }

    @Test
    fun `full week daily vs excluding holidays`() {
        val all = DayOfWeek.entries.toSet()
        assertEquals(RepeatSummary.Daily, repeatSummary(all, excludeHolidays = false))
        assertEquals(RepeatSummary.ExcludingHolidays, repeatSummary(all, excludeHolidays = true))
    }

    @Test
    fun `weekdays collapses to Weekdays`() {
        assertEquals(
            RepeatSummary.Weekdays,
            repeatSummary(WEEKDAYS, excludeHolidays = false),
        )
    }

    @Test
    fun `arbitrary days produce sorted resId list`() {
        val s = repeatSummary(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY), excludeHolidays = false)
        assertTrue(s is RepeatSummary.Days)
        val ids = (s as RepeatSummary.Days).dayNameResIds
        assertEquals(listOf(R.string.alarm_monday, R.string.alarm_wednesday), ids)
    }
}
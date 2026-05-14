package com.rabbithole.musicbbit.domain.helper

import com.rabbithole.musicbbit.domain.model.Holiday
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class WorkdayCheckerTest {

    // ── No holiday data (holiday = null) ──────────────────────────

    @Test
    fun `weekday with no holiday is a workday`() {
        assertTrue(isWorkday("2026-05-14", DayOfWeek.THURSDAY, null))
    }

    @Test
    fun `saturday with no holiday is not a workday`() {
        assertFalse(isWorkday("2026-05-16", DayOfWeek.SATURDAY, null))
    }

    @Test
    fun `sunday with no holiday is not a workday`() {
        assertFalse(isWorkday("2026-05-17", DayOfWeek.SUNDAY, null))
    }

    // ── Holiday data present ──────────────────────────────────────

    @Test
    fun `holiday that is a day off is not a workday`() {
        val holiday = Holiday(
            date = "2026-01-01",
            year = 2026,
            name = "元旦",
            isHoliday = true
        )
        assertFalse(isWorkday("2026-01-01", DayOfWeek.THURSDAY, holiday))
    }

    @Test
    fun `holiday that is an adjusted workday is a workday`() {
        // e.g. a Saturday that is a "调休" (compensatory workday)
        val holiday = Holiday(
            date = "2026-01-31",
            year = 2026,
            name = "春节调休",
            isHoliday = false
        )
        assertTrue(isWorkday("2026-01-31", DayOfWeek.SATURDAY, holiday))
    }

    @Test
    fun `statutory holiday on weekend is not a workday`() {
        // Holiday entry explicitly marks it as day off, even on a weekend
        val holiday = Holiday(
            date = "2026-05-16",
            year = 2026,
            name = "test holiday",
            isHoliday = true
        )
        assertFalse(isWorkday("2026-05-16", DayOfWeek.SATURDAY, holiday))
    }
}

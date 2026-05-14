package com.rabbithole.musicbbit.domain.helper

import com.rabbithole.musicbbit.domain.model.Holiday
import java.time.DayOfWeek

/**
 * Pure decision function: is [date] a working day?
 *
 * The caller supplies the holiday data; this function makes the pure boolean decision.
 * Weekends (SAT/SUN) are non-working unless a holiday entry marks the date as an adjusted workday
 * (isHoliday=false).
 *
 * @param date ISO date string (YYYY-MM-DD) — kept for logging context in callers
 * @param dayOfWeek the day-of-week for [date]
 * @param holiday nullable [Holiday] record for [date]; null means "no holiday data available"
 * @return true if the date should be treated as a workday
 */
fun isWorkday(
    date: String,
    dayOfWeek: DayOfWeek,
    holiday: Holiday?
): Boolean {
    if (holiday != null) {
        // isHoliday=true means statutory holiday (day off), isHoliday=false means adjusted workday
        return !holiday.isHoliday
    }
    // No holiday data: fall back to weekend/weekday logic
    return dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}

package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.annotation.StringRes
import com.rabbithole.musicbbit.R
import java.time.DayOfWeek

/** resId for the full name of the day (used in repeat-rule summary chips). */
@get:StringRes
val DayOfWeek.fullNameRes: Int
    get() = when (this) {
        DayOfWeek.MONDAY -> R.string.alarm_monday
        DayOfWeek.TUESDAY -> R.string.alarm_tuesday
        DayOfWeek.WEDNESDAY -> R.string.alarm_wednesday
        DayOfWeek.THURSDAY -> R.string.alarm_thursday
        DayOfWeek.FRIDAY -> R.string.alarm_friday
        DayOfWeek.SATURDAY -> R.string.alarm_saturday
        DayOfWeek.SUNDAY -> R.string.alarm_sunday
    }

/** resId for the short label (M/T/W/…) used on the day-name strip in DayOfWeekSelector. */
@get:StringRes
val DayOfWeek.shortLabelRes: Int
    get() = when (this) {
        DayOfWeek.MONDAY -> R.string.alarm_day_short_mon
        DayOfWeek.TUESDAY -> R.string.alarm_day_short_tue
        DayOfWeek.WEDNESDAY -> R.string.alarm_day_short_wed
        DayOfWeek.THURSDAY -> R.string.alarm_day_short_thu
        DayOfWeek.FRIDAY -> R.string.alarm_day_short_fri
        DayOfWeek.SATURDAY -> R.string.alarm_day_short_sat
        DayOfWeek.SUNDAY -> R.string.alarm_day_short_sun
    }

/** Standard Mon–Fri set, reused across alarm UI. */
val WEEKDAYS: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

/** Repeat-rule summary as pure structure — screens resolve resIds via stringResource. */
sealed interface RepeatSummary {
    data object OneTime : RepeatSummary
    data object Daily : RepeatSummary
    data object ExcludingHolidays : RepeatSummary
    data object Weekdays : RepeatSummary
    data class Days(val dayNameResIds: List<Int>) : RepeatSummary
}

/**
 * Pure function — no `stringResource` (must stay JVM-testable). Screens do the
 * resource lookup against the returned structure.
 */
fun repeatSummary(days: Set<DayOfWeek>, excludeHolidays: Boolean): RepeatSummary = when {
    days.isEmpty() -> RepeatSummary.OneTime
    days.size == 7 && !excludeHolidays -> RepeatSummary.Daily
    days.size == 7 -> RepeatSummary.ExcludingHolidays
    days == WEEKDAYS -> RepeatSummary.Weekdays
    else -> RepeatSummary.Days(days.sortedBy { it.value }.map { it.fullNameRes })
}
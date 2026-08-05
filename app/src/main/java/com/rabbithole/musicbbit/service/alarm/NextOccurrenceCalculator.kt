package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import java.time.DayOfWeek
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Calculates the next time an alarm should fire, with Chinese holiday / adjusted-workday awareness.
 *
 * Single path: [nextOccurrence] consults [HolidayRepository] to skip statutory holidays and
 * honour adjusted workdays. If no valid ring day is found within a 2-year search window, the
 * function throws [IllegalStateException] — callers must handle this explicitly (the previous
 * silent-fallback path was removed to comply with ADR 0007 "prefer errors over silent
 * degradation"). [HolidayRepository.isWorkday] itself has a Room → assets fallback chain, so
 * genuine "no data" scenarios are limited to "every selected day is excluded forever".
 */
@Singleton
class NextOccurrenceCalculator @Inject constructor(
    private val holidayRepository: HolidayRepository,
    private val clock: Clock,
) {

    suspend fun nextOccurrence(hour: Int, minute: Int, repeatDays: Set<DayOfWeek>, excludeHolidays: Boolean = false): Long {
        val now = Calendar.getInstance().apply { timeInMillis = clock.nowMs() }
        val year = now.get(Calendar.YEAR)
        holidayRepository.maybeRefreshHolidays(year).onFailure { error ->
            Timber.w(error, "Holiday refresh failed; falling back to local Room/assets cache via isWorkday")
        }
        val candidate = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (repeatDays.isEmpty() && candidate.before(now)) {
            candidate.add(Calendar.DAY_OF_MONTH, 1)
        }

        val shouldRing = ringPolicy(repeatDays, excludeHolidays)

        Timber.d("nextOccurrence start: now=${now.time}, candidate=${candidate.time}, repeatDays=$repeatDays, excludeHolidays=$excludeHolidays")

        while (true) {
            val dateStr = formatDate(candidate)
            val dayOfWeek = candidate.toDayOfWeek()
            val dayInfo = DayCandidate(
                dayOfWeek = dayOfWeek,
                isSelectedDay = dayOfWeek in repeatDays,
                isWorkday = holidayRepository.isWorkday(dateStr),
                isWeekend = candidate.get(Calendar.DAY_OF_WEEK) in WEEKEND_DAYS,
            )
            val isBeforeNow = candidate.before(now)

            Timber.v("Checking $dateStr (${dayOfWeek.name}): beforeNow=$isBeforeNow, dayInfo=$dayInfo")

            if (!isBeforeNow && shouldRing(dayInfo)) {
                Timber.i("nextOccurrence result: $dateStr $hour:$minute")
                return candidate.timeInMillis
            }

            candidate.add(Calendar.DAY_OF_MONTH, 1)

            if (candidate.get(Calendar.YEAR) > now.get(Calendar.YEAR) + 1) {
                // Hard error per ADR 0007: do not silently fall back to a non-holiday-aware
                // path. Caller (AlarmScheduler / AlarmStartupReconciler) is responsible for
                // try/catch and skipping the offending alarm rather than aborting the batch.
                throw IllegalStateException("No valid ring day found within 2-year search window")
            }
        }
    }
}

// -------------------------------------------------------------------------
// Ring policy — pure functions that decide whether a candidate day should ring.
// -------------------------------------------------------------------------

private val WEEKEND_DAYS = setOf(Calendar.SATURDAY, Calendar.SUNDAY)

private data class DayCandidate(
    val dayOfWeek: DayOfWeek,
    val isSelectedDay: Boolean,
    val isWorkday: Boolean,
    val isWeekend: Boolean,
)

/**
 * Returns a policy function for the given alarm configuration.
 *
 * The policy is a pure function: given a [DayCandidate], it returns `true` if the
 * alarm should ring on that day. Separating the decision from the date iteration
 * keeps the search loop shallow and makes each rule independently testable.
 */
private fun ringPolicy(
    repeatDays: Set<DayOfWeek>,
    excludeHolidays: Boolean,
): (DayCandidate) -> Boolean = when {
    // One-time alarm, normal mode: ring on the scheduled date
    repeatDays.isEmpty() && !excludeHolidays -> {
        { _ -> true }
    }
    // One-time alarm, exclude holidays: ring only on workdays
    repeatDays.isEmpty() && excludeHolidays -> {
        { it.isWorkday }
    }
    // Repeat alarm, normal mode: ring on selected days
    repeatDays.isNotEmpty() && !excludeHolidays -> {
        { it.isSelectedDay }
    }
    // Repeat alarm, exclude holidays: ring on selected workdays OR adjusted workdays
    repeatDays.isNotEmpty() && excludeHolidays -> {
        { candidate ->
            (candidate.isSelectedDay && candidate.isWorkday) ||
            (candidate.isWorkday && candidate.isWeekend)
        }
    }
    else -> {
        { _ -> false }
    }
}

private fun formatDate(calendar: Calendar): String = String.format(
    "%04d-%02d-%02d",
    calendar.get(Calendar.YEAR),
    calendar.get(Calendar.MONTH) + 1,
    calendar.get(Calendar.DAY_OF_MONTH),
)

private fun Calendar.toDayOfWeek(): DayOfWeek = when (get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> DayOfWeek.MONDAY
    Calendar.TUESDAY -> DayOfWeek.TUESDAY
    Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
    Calendar.THURSDAY -> DayOfWeek.THURSDAY
    Calendar.FRIDAY -> DayOfWeek.FRIDAY
    Calendar.SATURDAY -> DayOfWeek.SATURDAY
    Calendar.SUNDAY -> DayOfWeek.SUNDAY
    else -> DayOfWeek.MONDAY
}

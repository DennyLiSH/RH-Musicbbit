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
 * Two paths:
 *  - [nextOccurrence] is the production path. It consults [HolidayRepository] to skip statutory
 *    holidays and to honour adjusted workdays (a weekend that the calendar promotes to a
 *    working day for a make-up shift).
 *  - [Companion.nextOccurrenceFallback] is the pure-Kotlin fallback used when the holiday
 *    source is unreachable or the search exceeds a hard cap. It is also exposed for tests.
 */
@Singleton
class NextOccurrenceCalculator @Inject constructor(
    private val holidayRepository: HolidayRepository,
    private val clock: Clock,
) {

    suspend fun nextOccurrence(hour: Int, minute: Int, repeatDays: Set<DayOfWeek>, excludeHolidays: Boolean = false): Long {
        val now = Calendar.getInstance().apply { timeInMillis = clock.nowMs() }
        val year = now.get(Calendar.YEAR)
        holidayRepository.maybeRefreshHolidays(year)
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
                Timber.w("Could not find valid workday within search window, falling back to basic calculation")
                return nextOccurrenceFallback(hour, minute, repeatDays, now)
            }
        }
    }

    companion object {
        /**
         * Pure-Kotlin fallback that ignores holiday data. Test-visible: pass a fixed [now] for
         * deterministic results.
         */
        fun nextOccurrenceFallback(
            hour: Int,
            minute: Int,
            repeatDays: Set<DayOfWeek>,
            now: Calendar,
        ): Long {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (repeatDays.isEmpty()) {
                if (candidate.before(now)) {
                    candidate.add(Calendar.DAY_OF_MONTH, 1)
                }
            } else {
                while (candidate.before(now) || candidate.toDayOfWeek() !in repeatDays) {
                    candidate.add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            return candidate.timeInMillis
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

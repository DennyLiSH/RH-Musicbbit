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

        Timber.d("nextOccurrence start: now=${now.time}, candidate=${candidate.time}, repeatDays=$repeatDays, excludeHolidays=$excludeHolidays")

        while (true) {
            val dateStr = String.format(
                "%04d-%02d-%02d",
                candidate.get(Calendar.YEAR),
                candidate.get(Calendar.MONTH) + 1,
                candidate.get(Calendar.DAY_OF_MONTH),
            )
            val isWorkday = holidayRepository.isWorkday(dateStr)
            val dayMatches = candidate.toDayOfWeek() in repeatDays || repeatDays.isEmpty()
            val dayOfWeekName = candidate.toDayOfWeek().name
            val isBeforeNow = candidate.before(now)

            Timber.v("Checking $dateStr ($dayOfWeekName): beforeNow=$isBeforeNow, dayMatches=$dayMatches, isWorkday=$isWorkday")

            if (isBeforeNow) {
                candidate.add(Calendar.DAY_OF_MONTH, 1)
                continue
            }

            if (excludeHolidays) {
                // Excluding holidays: ring only on workdays
                if (repeatDays.isEmpty()) {
                    // One-time alarm with holiday exclusion: ring only on workdays
                    if (isWorkday) {
                        Timber.i("nextOccurrence result (one-time workday): $dateStr $hour:$minute")
                        return candidate.timeInMillis
                    }
                } else {
                    // Repeat alarm with holiday exclusion: ring on selected workdays,
                    // or on adjusted workdays (weekend make-up shifts)
                    if (dayMatches && isWorkday) {
                        Timber.i("nextOccurrence result (weekday match): $dateStr $hour:$minute")
                        return candidate.timeInMillis
                    }
                    if (!dayMatches && isWorkday) {
                        val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
                        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                            Timber.i("nextOccurrence result (adjusted workday): $dateStr $hour:$minute")
                            return candidate.timeInMillis
                        }
                    }
                }
            } else {
                // Normal mode (cron-style): ring on the scheduled date regardless of holidays
                if (repeatDays.isEmpty()) {
                    Timber.i("nextOccurrence result (one-time): $dateStr $hour:$minute")
                    return candidate.timeInMillis
                } else {
                    if (dayMatches) {
                        Timber.i("nextOccurrence result (day match): $dateStr $hour:$minute")
                        return candidate.timeInMillis
                    }
                }
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

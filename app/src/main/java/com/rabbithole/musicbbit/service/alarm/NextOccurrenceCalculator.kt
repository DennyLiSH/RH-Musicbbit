package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.usecase.IsWorkdayUseCase
import java.time.LocalDate
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Calculates the next time an alarm should fire, with Chinese holiday / adjusted-workday awareness.
 *
 * Two paths:
 *  - [nextOccurrence] is the production path. It consults [IsWorkdayUseCase] to skip statutory
 *    holidays and to honour adjusted workdays (a weekend that the calendar promotes to a
 *    working day for a make-up shift).
 *  - [Companion.nextOccurrenceFallback] is the pure-Kotlin fallback used when the holiday
 *    source is unreachable or the search exceeds a hard cap. It is also exposed for tests.
 */
@Singleton
class NextOccurrenceCalculator @Inject constructor(
    private val isWorkdayUseCase: IsWorkdayUseCase,
    private val clock: Clock,
) {

    suspend fun nextOccurrence(hour: Int, minute: Int, repeatDaysBitmask: Int, excludeHolidays: Boolean = false): Long {
        val now = Calendar.getInstance().apply { timeInMillis = clock.nowMs() }
        val candidate = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (repeatDaysBitmask == 0 && candidate.before(now)) {
            candidate.add(Calendar.DAY_OF_MONTH, 1)
        }

        while (true) {
            val dateStr = String.format(
                "%04d-%02d-%02d",
                candidate.get(Calendar.YEAR),
                candidate.get(Calendar.MONTH) + 1,
                candidate.get(Calendar.DAY_OF_MONTH),
            )
            val isWorkday = isWorkdayUseCase(LocalDate.parse(dateStr))
            val dayMatches = isDayMatchingBitmask(candidate, repeatDaysBitmask) || repeatDaysBitmask == 0

            if (candidate.before(now)) {
                candidate.add(Calendar.DAY_OF_MONTH, 1)
                continue
            }

            if (repeatDaysBitmask == 0) {
                if (isWorkday) return candidate.timeInMillis
            } else {
                val isEveryday = repeatDaysBitmask == 0b1111111

                if (!excludeHolidays && isEveryday) {
                    // Daily mode: ring every day regardless of holidays
                    if (dayMatches) return candidate.timeInMillis
                } else {
                    // All other modes (including excluding-holidays): check workday + adjusted workday logic
                    if (dayMatches && isWorkday) return candidate.timeInMillis
                    if (!dayMatches && isWorkday) {
                        val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
                        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                            return candidate.timeInMillis
                        }
                    }
                }
            }

            candidate.add(Calendar.DAY_OF_MONTH, 1)

            if (candidate.get(Calendar.YEAR) > now.get(Calendar.YEAR) + 1) {
                Timber.w("Could not find valid workday within search window, falling back to basic calculation")
                return nextOccurrenceFallback(hour, minute, repeatDaysBitmask, now)
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
            repeatDaysBitmask: Int,
            now: Calendar,
        ): Long {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (repeatDaysBitmask == 0) {
                if (candidate.before(now)) {
                    candidate.add(Calendar.DAY_OF_MONTH, 1)
                }
            } else {
                while (candidate.before(now) || !isDayMatchingBitmask(candidate, repeatDaysBitmask)) {
                    candidate.add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            return candidate.timeInMillis
        }

        private fun isDayMatchingBitmask(calendar: Calendar, bitmask: Int): Boolean {
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val bit = when (dayOfWeek) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> return false
            }
            return bitmask and (1 shl bit) != 0
        }
    }
}

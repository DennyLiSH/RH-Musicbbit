package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import javax.inject.Inject

/**
 * Use case that saves (inserts or updates) an alarm.
 * Returns the ID of the saved alarm wrapped in a Result.
 */
class SaveAlarmUseCase @Inject constructor(
    private val alarmRepository: AlarmRepository
) {
    suspend operator fun invoke(alarm: Alarm): Result<Long> = runCatching {
        alarmRepository.saveAlarm(alarm)
    }
}

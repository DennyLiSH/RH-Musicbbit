package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import javax.inject.Inject

/**
 * Use case that deletes an alarm.
 */
class DeleteAlarmUseCase @Inject constructor(
    private val alarmRepository: AlarmRepository
) {
    suspend operator fun invoke(alarm: Alarm): Result<Unit> = runCatching {
        alarmRepository.deleteAlarm(alarm)
    }
}

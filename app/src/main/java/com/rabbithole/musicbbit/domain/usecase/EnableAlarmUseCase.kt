package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import javax.inject.Inject

/**
 * Use case that toggles the enabled state of an alarm.
 */
class EnableAlarmUseCase @Inject constructor(
    private val alarmRepository: AlarmRepository
) {
    suspend operator fun invoke(id: Long, enabled: Boolean): Result<Unit> = runCatching {
        alarmRepository.enableAlarm(id, enabled)
    }
}

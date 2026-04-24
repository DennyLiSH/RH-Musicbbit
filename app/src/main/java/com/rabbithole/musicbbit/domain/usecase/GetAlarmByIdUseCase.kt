package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import javax.inject.Inject

/**
 * Use case that returns a single alarm by its ID.
 */
class GetAlarmByIdUseCase @Inject constructor(
    private val alarmRepository: AlarmRepository
) {
    suspend operator fun invoke(id: Long): Alarm? = alarmRepository.getAlarmById(id)
}

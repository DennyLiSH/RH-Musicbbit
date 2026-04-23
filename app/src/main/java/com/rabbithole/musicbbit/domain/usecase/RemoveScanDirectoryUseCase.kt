package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import javax.inject.Inject

class RemoveScanDirectoryUseCase @Inject constructor(
    private val scanDirectoryRepository: ScanDirectoryRepository
) {
    suspend operator fun invoke(id: Long): Result<Unit> {
        return scanDirectoryRepository.remove(id)
    }
}

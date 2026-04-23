package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetScanDirectoriesUseCase @Inject constructor(
    private val scanDirectoryRepository: ScanDirectoryRepository
) {
    operator fun invoke(): Flow<List<ScanDirectory>> {
        return scanDirectoryRepository.getAll()
    }
}

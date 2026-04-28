package com.rabbithole.musicbbit.domain.usecase

import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import timber.log.Timber
import javax.inject.Inject

class AddScanDirectoryUseCase @Inject constructor(
    private val scanDirectoryRepository: ScanDirectoryRepository,
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(directory: ScanDirectory): Result<Long> {
        Timber.i("AddScanDirectoryUseCase: adding directory=${directory.path}")
        val result = scanDirectoryRepository.add(directory)
        if (result.isSuccess) {
            musicRepository.refreshSongs()
        }
        return result
    }
}

package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.ScanDirectory
import kotlinx.coroutines.flow.Flow

interface ScanDirectoryRepository {
    fun getAll(): Flow<List<ScanDirectory>>
    suspend fun add(directory: ScanDirectory): Result<Long>
    suspend fun remove(id: Long): Result<Unit>
}

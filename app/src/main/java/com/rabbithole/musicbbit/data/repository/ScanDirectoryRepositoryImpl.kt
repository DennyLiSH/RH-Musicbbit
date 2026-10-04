package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.LibraryRefresher
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.mapper.toDomain
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlinx.coroutines.withContext
import timber.log.Timber

class ScanDirectoryRepositoryImpl @Inject constructor(
    private val scanDirectoryDao: ScanDirectoryDao,
    private val libraryRefresher: LibraryRefresher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ScanDirectoryRepository {

    override fun getAll(): Flow<List<ScanDirectory>> {
        return scanDirectoryDao.getAll()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun add(directory: ScanDirectory): Result<Long> =
        libraryRefresher.addDirectoryAndRefresh(directory)

    override suspend fun remove(id: Long): Result<Unit> = withContext(ioDispatcher) {
        try {
            libraryRefresher.removeDirectoryAndCascade(id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove scan directory: id=$id")
            Result.failure(e)
        }
    }
}
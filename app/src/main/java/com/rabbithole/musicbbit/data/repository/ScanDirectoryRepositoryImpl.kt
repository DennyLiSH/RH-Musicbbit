package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.model.ScanDirectoryEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ScanDirectoryRepositoryImpl @Inject constructor(
    private val scanDirectoryDao: ScanDirectoryDao,
    private val songDao: SongDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ScanDirectoryRepository {

    override fun getAll(): Flow<List<ScanDirectory>> {
        return scanDirectoryDao.getAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun add(directory: ScanDirectory): Result<Long> = withContext(ioDispatcher) {
        try {
            val id = scanDirectoryDao.insert(directory.toEntity())
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun remove(id: Long): Result<Unit> = withContext(ioDispatcher) {
        try {
            val directory = scanDirectoryDao.getById(id)
            if (directory != null) {
                // Delete all songs under this directory path
                val allSongs = songDao.getAll()
                val songsToDelete = allSongs.firstOrNull()?.filter {
                    it.path.startsWith(directory.path)
                } ?: emptyList()
                songsToDelete.forEach { songDao.delete(it) }

                scanDirectoryDao.delete(directory)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ScanDirectoryEntity.toDomain(): ScanDirectory {
        return ScanDirectory(
            id = id,
            path = path,
            name = name,
            addedAt = addedAt
        )
    }

    private fun ScanDirectory.toEntity(): ScanDirectoryEntity {
        return ScanDirectoryEntity(
            id = id,
            path = path,
            name = name,
            addedAt = addedAt
        )
    }
}

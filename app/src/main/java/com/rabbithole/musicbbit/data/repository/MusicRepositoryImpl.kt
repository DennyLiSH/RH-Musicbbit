package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.MusicScanner
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.model.SongEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val scanDirectoryDao: ScanDirectoryDao,
    private val musicScanner: MusicScanner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MusicRepository {

    override fun getAllSongs(): Flow<List<Song>> {
        return songDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchSongs(query: String): Flow<List<Song>> {
        return songDao.getAll().map { entities ->
            entities.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist?.contains(query, ignoreCase = true) == true
            }.map { it.toDomain() }
        }
    }

    override suspend fun refreshSongs(): Result<Unit> = withContext(ioDispatcher) {
        try {
            val directories = scanDirectoryDao.getAll()
            val paths = directories.firstOrNull()?.map { it.path } ?: emptyList()

            if (paths.isEmpty()) {
                songDao.deleteAll()
                return@withContext Result.success(Unit)
            }

            val scanned = musicScanner.scanDirectories(paths)
            val existing = songDao.getAll().firstOrNull() ?: emptyList()

            val existingMap = existing.associateBy { it.path }
            val scannedMap = scanned.associateBy { it.path }

            val toInsert = scanned.filter { it.path !in existingMap }
            val toDelete = existing.filter { it.path !in scannedMap }
            val toUpdate = scanned.filter {
                val existingSong = existingMap[it.path]
                existingSong != null && existingSong != it.copy(id = existingSong.id)
            }.map {
                it.copy(id = existingMap[it.path]!!.id)
            }

            if (toDelete.isNotEmpty()) {
                toDelete.forEach { songDao.delete(it) }
            }
            if (toInsert.isNotEmpty()) {
                songDao.insertAll(toInsert)
            }
            if (toUpdate.isNotEmpty()) {
                toUpdate.forEach { songDao.update(it) }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun SongEntity.toDomain(): Song {
        return Song(
            id = id,
            path = path,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            dateAdded = dateAdded,
            coverUri = coverUri
        )
    }
}

package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.MusicScanner
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.local.sync.SongSyncEngine
import com.rabbithole.musicbbit.data.mapper.toDomain
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val scanDirectoryDao: ScanDirectoryDao,
    private val musicScanner: MusicScanner,
    private val songSyncEngine: SongSyncEngine,
    private val songSorter: SongSorter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MusicRepository {

    override fun getAllSongs(): Flow<List<Song>> {
        return songDao.getAll()
            .map { entities -> entities.map { it.toDomain() } }
            .map { songs -> songSorter.sort(songs) }
            .distinctUntilChanged()
    }

    override fun searchSongs(query: String): Flow<List<Song>> {
        return songDao.searchSongs(query)
            .map { entities -> entities.map { it.toDomain() } }
            .map { songs -> songSorter.sort(songs) }
            .distinctUntilChanged()
    }

    override suspend fun refreshSongs(): Result<Unit> = withContext(ioDispatcher) {
        try {
            val directories = scanDirectoryDao.getAll()
            val paths = directories.firstOrNull()?.map { it.path } ?: emptyList()

            Timber.i("Refreshing songs from ${paths.size} scan directories")

            if (paths.isEmpty()) {
                songDao.deleteAll()
                Timber.i("No scan directories, cleared all songs")
                return@withContext Result.success(Unit)
            }

            val scanned = musicScanner.scanDirectories(paths)
            val existing = songDao.getAll().firstOrNull() ?: emptyList()

            val result = songSyncEngine.sync(scanned, existing)

            Timber.i(
                "Song refresh complete: scanned=${scanned.size}, " +
                    "inserted=${result.inserted}, deleted=${result.deleted}, updated=${result.updated}"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh songs")
            Result.failure(e)
        }
    }

    override suspend fun refreshDirectory(directoryPath: String): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                Timber.i("Refreshing directory: $directoryPath")

                val scanned = musicScanner.scanDirectories(listOf(directoryPath))
                val existing = songDao.getByPathPrefix(directoryPath).firstOrNull() ?: emptyList()
                val result = songSyncEngine.sync(scanned, existing)

                Timber.i(
                    "Directory refresh complete: $directoryPath, " +
                        "inserted=${result.inserted}, deleted=${result.deleted}, updated=${result.updated}"
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh directory: $directoryPath")
                Result.failure(e)
            }
        }

}

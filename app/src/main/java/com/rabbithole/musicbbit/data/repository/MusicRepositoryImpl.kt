package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.LibraryRefresher
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.mapper.toDomain
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val scanDirectoryDao: ScanDirectoryDao,
    private val libraryRefresher: LibraryRefresher,
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
        Timber.i("Refreshing songs")
        libraryRefresher.refreshAll()
            .onSuccess { r ->
                Timber.i(
                    "Song refresh complete: inserted=${r.inserted}, " +
                        "deleted=${r.deleted}, updated=${r.updated}"
                )
            }
            .map { }
            .onFailure { Timber.e(it, "Failed to refresh songs") }
    }

    override suspend fun refreshDirectory(directoryPath: String): Result<Unit> =
        withContext(ioDispatcher) {
            Timber.i("Refreshing directory: $directoryPath")
            libraryRefresher.refreshDirectory(directoryPath)
                .onSuccess { r ->
                    Timber.i(
                        "Directory refresh complete: $directoryPath, " +
                            "inserted=${r.inserted}, deleted=${r.deleted}, updated=${r.updated}"
                    )
                }
                .map { }
                .onFailure { Timber.e(it, "Failed to refresh directory: $directoryPath") }
        }

}

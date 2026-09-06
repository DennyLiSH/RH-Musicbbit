package com.rabbithole.musicbbit.data.local

import com.rabbithole.musicbbit.data.local.sync.SongSyncEngine
import com.rabbithole.musicbbit.data.local.sync.SyncResult
import com.rabbithole.musicbbit.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Deep module owning the refresh recipe: given a set of directories, produce a synced
 * library. One implementation of "scan → load existing → [SongSyncEngine.sync] in a
 * transaction → report [SyncResult]", used for both full-library and per-directory
 * refreshes.
 *
 * Policy made explicit here: when there are no scan directories at all, the library is
 * cleared — an empty directory list means "the user unlinked every source", not "keep
 * whatever is left over". A per-directory refresh never clears.
 */
@Singleton
class LibraryRefresher @Inject constructor(
    private val musicScanner: MusicScanner,
    private val songDao: com.rabbithole.musicbbit.data.local.dao.SongDao,
    private val scanDirectoryDao: com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao,
    private val songSyncEngine: SongSyncEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun refreshAll(): Result<SyncResult> = withContext(ioDispatcher) {
        runCatching {
            val paths = scanDirectoryDao.getAll().firstOrNull()?.map { it.path } ?: emptyList()
            if (paths.isEmpty()) {
                songDao.deleteAll()
                return@runCatching SyncResult(inserted = 0, deleted = 0, updated = 0)
            }
            val scanned = musicScanner.scanDirectories(paths)
            val existing = songDao.getAll().firstOrNull() ?: emptyList()
            toSyncResult(scanned, existing)
        }
    }

    suspend fun refreshDirectory(directoryPath: String): Result<SyncResult> = withContext(ioDispatcher) {
        runCatching {
            val scanned = musicScanner.scanDirectories(listOf(directoryPath))
            val existing = songDao.getByPathPrefix(directoryPath).firstOrNull() ?: emptyList()
            toSyncResult(scanned, existing)
        }
    }

    private suspend fun toSyncResult(
        scanned: List<com.rabbithole.musicbbit.domain.model.Song>,
        existing: List<com.rabbithole.musicbbit.data.local.model.SongEntity>,
    ): SyncResult = songSyncEngine.sync(scanned, existing)
}

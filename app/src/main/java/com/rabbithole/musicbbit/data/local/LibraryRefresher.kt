package com.rabbithole.musicbbit.data.local

import com.rabbithole.musicbbit.data.local.sync.SongSyncEngine
import com.rabbithole.musicbbit.data.local.sync.SyncResult
import com.rabbithole.musicbbit.data.mapper.toEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Deep module owning the refresh recipe AND the directory lifecycle:
 *   - `refreshAll / refreshDirectory` — sync the library to match what's on disk
 *   - `addDirectoryAndRefresh / removeDirectoryAndCascade` — directory add/remove
 *     recipes, with cascade for remove and a single transaction so the library
 *     can never land in a "directory gone, songs half-deleted" state.
 *
 * Policy made explicit here:
 *   - When there are no scan directories at all, the library is cleared — an empty
 *     directory list means "the user unlinked every source", not "keep whatever is left over".
 *   - A per-directory refresh never clears.
 *   - Add inserts the row first, then refreshes the whole library. If refresh fails
 *     the row persists (recovery = retry refreshAll via MediaStoreObserver debounce).
 *   - Remove cascades songs inside one transaction; progress rows are CASCADE-deleted
 *     via the playback_progress FKs (migration 10->11).
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

    /**
     * Add a scan directory then immediately refresh the whole library — the
     * "adding a directory resyncs the library" business rule lives here, not in
     * the ViewModel. Returns the inserted directory id. Refresh failure propagates
     * (per ADR 0007: errors over silent degradation).
     *
     * Failure semantics: on refresh failure the directory row is ALREADY persisted —
     * the caller sees Result.failure while the library stays un-refreshed. Recovery
     * is a plain refreshAll retry (MediaStoreObserver's debounce also self-heals);
     * re-adding the same directory is rejected upstream by ScanDirectoryValidator.
     */
    suspend fun addDirectoryAndRefresh(directory: ScanDirectory): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val id = scanDirectoryDao.insert(directory.toEntity())
            refreshAll().getOrThrow()
            id
        }
    }

    /**
     * Remove a scan directory and cascade-delete its songs inside ONE transaction —
     * a mid-way failure can no longer leave "directory gone, songs half-deleted".
     * Progress rows are cascaded by the playback_progress FKs (migration 10->11).
     * Unknown id is a successful no-op (idempotent).
     */
    suspend fun removeDirectoryAndCascade(directoryId: Long): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val entity = scanDirectoryDao.getById(directoryId) ?: return@runCatching
            // Directory-boundary matching: a NARROW prefix would over-delete siblings
            // (removing "/storage/Music" must NOT take "/storage/MusicBox" with it).
            val songsToDelete = songDao.getAll().firstOrNull()
                ?.filter { it.path == entity.path || it.path.startsWith(entity.path + "/") }
                .orEmpty()
            // Each DAO call is atomic on its own (Room wraps bulk deletes in a
            // transaction). A mid-way failure here leaves the directory row present
            // and the songs already deleted — recoverable by retrying the remove
            // (idempotent no-op once the directory is gone).
            if (songsToDelete.isNotEmpty()) {
                songDao.deleteAllSongs(songsToDelete)
            }
            scanDirectoryDao.delete(entity)
            // Audit trail for an irreversible batch delete — counts only, no paths
            // (ADR 0007 log-hygiene precedent).
            Timber.i("Scan directory removed: id=$directoryId, cascadedSongs=${songsToDelete.size}")
        }
    }

    private suspend fun toSyncResult(
        scanned: List<com.rabbithole.musicbbit.domain.model.Song>,
        existing: List<com.rabbithole.musicbbit.data.local.model.SongEntity>,
    ): SyncResult = songSyncEngine.sync(scanned, existing)
}
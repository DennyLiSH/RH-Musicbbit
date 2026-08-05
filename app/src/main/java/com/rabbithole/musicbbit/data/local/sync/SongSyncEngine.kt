package com.rabbithole.musicbbit.data.local.sync

import androidx.room.withTransaction
import com.rabbithole.musicbbit.data.local.AppDatabase
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.local.model.SongEntity
import com.rabbithole.musicbbit.data.mapper.toEntity
import com.rabbithole.musicbbit.domain.model.Song
import javax.inject.Inject

/**
 * 歌曲同步引擎（深模块）
 *
 * 封装歌曲同步的差异计算与原子应用，是一个深模块。
 *
 * 职责：
 * - 对比扫描结果与数据库现有歌曲
 * - 计算需要插入、删除、更新的歌曲（[computeDiff]，纯函数）
 * - 在 Room 单一事务内原子应用 diff（[sync]），中途失败自动回滚
 * - 保留现有歌曲的 ID（更新场景）
 *
 * 异常处理契约：[sync] 内 insert/update/delete 抛异常时整个事务自动回滚，
 * 异常向上传播到调用方（如 Repository 的 Result.failure 包装层）。
 */
class SongSyncEngine @Inject constructor(
    private val database: AppDatabase,
    private val songDao: SongDao,
) {
    /**
     * 计算扫描结果与数据库现有歌曲的差异（纯函数，可独立测试）。
     */
    fun computeDiff(
        scanned: List<Song>,
        existing: List<SongEntity>,
    ): SongDiff {
        val existingMap = existing.associateBy { it.path }
        val scannedMap = scanned.associate { it.path to it.toEntity() }

        val inserts = scannedMap.filterKeys { it !in existingMap }.values.toList()
        val deletes = existingMap.filterKeys { it !in scannedMap }.values.toList()
        val updates = scannedMap.filterKeys { it in existingMap }
            .mapNotNull { (path, scannedSong) ->
                val existingSong = existingMap[path]!!
                if (scannedSong.copy(id = existingSong.id) != existingSong) {
                    scannedSong.copy(id = existingSong.id)
                } else null
            }

        return SongDiff(inserts, deletes, updates)
    }

    /**
     * 在单一 Room 事务内原子应用 diff。中途失败自动回滚。
     *
     * @return [SyncResult] 仅含计数（脱敏，无文件路径），供调用方写日志
     */
    suspend fun sync(
        scanned: List<Song>,
        existing: List<SongEntity>,
    ): SyncResult {
        val diff = computeDiff(scanned, existing)
        database.withTransaction {
            if (diff.toDelete.isNotEmpty()) {
                songDao.deleteAllSongs(diff.toDelete)
            }
            if (diff.toInsert.isNotEmpty()) {
                songDao.insertAll(diff.toInsert)
            }
            if (diff.toUpdate.isNotEmpty()) {
                songDao.updateAll(diff.toUpdate)
            }
        }
        return SyncResult(
            inserted = diff.toInsert.size,
            deleted = diff.toDelete.size,
            updated = diff.toUpdate.size,
        )
    }
}

/**
 * 同步结果计数。仅含聚合数字，不含文件路径或文件名（脱敏，日志安全）。
 */
data class SyncResult(
    val inserted: Int,
    val deleted: Int,
    val updated: Int,
)

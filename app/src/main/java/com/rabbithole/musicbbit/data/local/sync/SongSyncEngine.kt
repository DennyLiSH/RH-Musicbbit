package com.rabbithole.musicbbit.data.local.sync

import com.rabbithole.musicbbit.data.local.model.SongEntity
import com.rabbithole.musicbbit.data.mapper.toEntity
import com.rabbithole.musicbbit.domain.model.Song
import javax.inject.Inject

/**
 * 歌曲同步引擎（深模块）
 *
 * 封装歌曲同步的差异计算逻辑，是一个纯函数模块。
 *
 * 职责：
 * - 对比扫描结果与数据库现有歌曲
 * - 计算需要插入、删除、更新的歌曲
 * - 保留现有歌曲的 ID（更新场景）
 *
 * 这是一个深模块：简单接口隐藏复杂的差异计算逻辑，易于测试和复用。
 */
class SongSyncEngine @Inject constructor() {
    /**
     * 计算扫描结果与数据库现有歌曲的差异
     *
     * @param scanned 扫描到的歌曲列表（来自 MediaStore）
     * @param existing 数据库现有歌曲列表（来自 Room）
     * @return 需要插入、删除、更新的歌曲差异
     */
    fun computeDiff(
        scanned: List<Song>,
        existing: List<SongEntity>
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
}

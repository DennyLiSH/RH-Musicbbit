package com.rabbithole.musicbbit.data.local.sync

import com.rabbithole.musicbbit.data.local.model.SongEntity

/**
 * 歌曲同步差异结果
 *
 * 表示扫描结果与数据库现有歌曲的差异，用于增量更新。
 *
 * @property toInsert 需要插入的新歌曲
 * @property toDelete 需要删除的歌曲（文件已不存在）
 * @property toUpdate 需要更新的歌曲（元数据已变化）
 */
data class SongDiff(
    val toInsert: List<SongEntity>,
    val toDelete: List<SongEntity>,
    val toUpdate: List<SongEntity>
)

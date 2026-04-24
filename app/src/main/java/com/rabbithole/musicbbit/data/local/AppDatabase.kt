package com.rabbithole.musicbbit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.local.dao.PlaybackProgressDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.data.model.PlaybackProgressEntity
import com.rabbithole.musicbbit.data.model.PlaylistEntity
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.data.model.ScanDirectoryEntity
import com.rabbithole.musicbbit.data.model.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlaybackProgressEntity::class,
        AlarmEntity::class,
        ScanDirectoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistSongDao(): PlaylistSongDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun alarmDao(): AlarmDao
    abstract fun scanDirectoryDao(): ScanDirectoryDao
}

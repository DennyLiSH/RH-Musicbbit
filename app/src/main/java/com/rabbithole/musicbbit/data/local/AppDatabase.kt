package com.rabbithole.musicbbit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.local.dao.HolidayDao
import com.rabbithole.musicbbit.data.local.dao.PlaybackProgressDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.local.model.HolidayEntity
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.data.model.AutoStopConverter
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.model.Song

@Database(
    entities = [
        Song::class,
        Playlist::class,
        PlaylistSongEntity::class,
        PlaybackProgress::class,
        AlarmEntity::class,
        ScanDirectory::class,
        HolidayEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(AutoStopConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistSongDao(): PlaylistSongDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun alarmDao(): AlarmDao
    abstract fun scanDirectoryDao(): ScanDirectoryDao
    abstract fun holidayDao(): HolidayDao
}

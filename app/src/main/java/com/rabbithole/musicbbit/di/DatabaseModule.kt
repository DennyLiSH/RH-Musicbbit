package com.rabbithole.musicbbit.di

import android.content.Context
import androidx.room.Room
import com.rabbithole.musicbbit.data.local.AppDatabase
import com.rabbithole.musicbbit.data.local.MIGRATION_2_3
import com.rabbithole.musicbbit.data.local.MIGRATION_3_4
import com.rabbithole.musicbbit.data.local.MIGRATION_4_5
import com.rabbithole.musicbbit.data.local.MIGRATION_5_6
import com.rabbithole.musicbbit.data.local.MIGRATION_6_7
import com.rabbithole.musicbbit.data.local.MIGRATION_7_8
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.local.dao.HolidayDao
import com.rabbithole.musicbbit.data.local.dao.PlaybackProgressDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.data.local.dao.ScanDirectoryDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "musicbbit_database"
        )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()
    }

    @Provides
    fun provideSongDao(database: AppDatabase): SongDao = database.songDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun providePlaylistSongDao(database: AppDatabase): PlaylistSongDao = database.playlistSongDao()

    @Provides
    fun providePlaybackProgressDao(database: AppDatabase): PlaybackProgressDao = database.playbackProgressDao()

    @Provides
    fun provideAlarmDao(database: AppDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideScanDirectoryDao(database: AppDatabase): ScanDirectoryDao = database.scanDirectoryDao()

    @Provides
    fun provideHolidayDao(database: AppDatabase): HolidayDao = database.holidayDao()
}

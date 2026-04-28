package com.rabbithole.musicbbit.di

import android.content.Context
import androidx.room.Room
import com.rabbithole.musicbbit.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Test module that replaces [DatabaseModule] with an in-memory Room database.
 *
 * Used by Hilt Robolectric tests to avoid disk I/O and ensure clean state per test.
 */
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
@Module
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideSongDao(database: AppDatabase) = database.songDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase) = database.playlistDao()

    @Provides
    fun providePlaylistSongDao(database: AppDatabase) = database.playlistSongDao()

    @Provides
    fun providePlaybackProgressDao(database: AppDatabase) = database.playbackProgressDao()

    @Provides
    fun provideAlarmDao(database: AppDatabase) = database.alarmDao()

    @Provides
    fun provideScanDirectoryDao(database: AppDatabase) = database.scanDirectoryDao()

    @Provides
    fun provideHolidayDao(database: AppDatabase) = database.holidayDao()
}

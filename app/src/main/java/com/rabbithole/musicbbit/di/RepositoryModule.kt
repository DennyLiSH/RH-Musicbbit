package com.rabbithole.musicbbit.di

import com.rabbithole.musicbbit.data.repository.AlarmPersistenceRepositoryImpl
import com.rabbithole.musicbbit.data.repository.AlarmRepositoryImpl
import com.rabbithole.musicbbit.data.repository.AlarmRingSettingsRepositoryImpl
import com.rabbithole.musicbbit.data.repository.HolidayRepositoryImpl
import com.rabbithole.musicbbit.data.repository.MusicRepositoryImpl
import com.rabbithole.musicbbit.data.repository.PlaybackProgressRepositoryImpl
import com.rabbithole.musicbbit.data.repository.PlaylistRepositoryImpl
import com.rabbithole.musicbbit.data.repository.ScanDirectoryRepositoryImpl
import com.rabbithole.musicbbit.data.repository.ThemeRepositoryImpl
import com.rabbithole.musicbbit.domain.repository.AlarmPersistenceRepository
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import com.rabbithole.musicbbit.domain.repository.ThemeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(
        impl: MusicRepositoryImpl
    ): MusicRepository

    @Binds
    @Singleton
    abstract fun bindScanDirectoryRepository(
        impl: ScanDirectoryRepositoryImpl
    ): ScanDirectoryRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(
        impl: PlaylistRepositoryImpl
    ): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackProgressRepository(
        impl: PlaybackProgressRepositoryImpl
    ): PlaybackProgressRepository

    @Binds
    @Singleton
    abstract fun bindThemeRepository(
        impl: ThemeRepositoryImpl
    ): ThemeRepository

    @Binds
    @Singleton
    abstract fun bindAlarmRepository(
        impl: AlarmRepositoryImpl
    ): AlarmRepository

    @Binds
    @Singleton
    abstract fun bindAlarmPersistenceRepository(
        impl: AlarmPersistenceRepositoryImpl
    ): AlarmPersistenceRepository

    @Binds
    @Singleton
    abstract fun bindHolidayRepository(
        impl: HolidayRepositoryImpl
    ): HolidayRepository

    @Binds
    @Singleton
    abstract fun bindAlarmRingSettingsRepository(
        impl: AlarmRingSettingsRepositoryImpl
    ): AlarmRingSettingsRepository
}

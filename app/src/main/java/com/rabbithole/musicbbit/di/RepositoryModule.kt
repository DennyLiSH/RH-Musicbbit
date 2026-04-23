package com.rabbithole.musicbbit.di

import com.rabbithole.musicbbit.data.repository.MusicRepositoryImpl
import com.rabbithole.musicbbit.data.repository.PlaybackProgressRepositoryImpl
import com.rabbithole.musicbbit.data.repository.PlaylistRepositoryImpl
import com.rabbithole.musicbbit.data.repository.ScanDirectoryRepositoryImpl
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMusicRepository(
        impl: MusicRepositoryImpl
    ): MusicRepository

    @Binds
    abstract fun bindScanDirectoryRepository(
        impl: ScanDirectoryRepositoryImpl
    ): ScanDirectoryRepository

    @Binds
    abstract fun bindPlaylistRepository(
        impl: PlaylistRepositoryImpl
    ): PlaylistRepository

    @Binds
    abstract fun bindPlaybackProgressRepository(
        impl: PlaybackProgressRepositoryImpl
    ): PlaybackProgressRepository
}

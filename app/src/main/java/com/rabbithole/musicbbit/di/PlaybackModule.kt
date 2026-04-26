package com.rabbithole.musicbbit.di

import com.rabbithole.musicbbit.service.playback.ExoPlayerAdapter
import com.rabbithole.musicbbit.service.playback.PlayerPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {

    @Binds
    @Singleton
    abstract fun bindPlayerPort(impl: ExoPlayerAdapter): PlayerPort
}

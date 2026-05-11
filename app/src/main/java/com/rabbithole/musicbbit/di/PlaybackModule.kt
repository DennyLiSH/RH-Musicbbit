package com.rabbithole.musicbbit.di

import com.rabbithole.musicbbit.service.AndroidServiceStarter
import com.rabbithole.musicbbit.service.AudioFocusManager
import com.rabbithole.musicbbit.service.MusicNotificationManager
import com.rabbithole.musicbbit.service.playback.AudioFocusPort
import com.rabbithole.musicbbit.service.playback.ExoPlayerAdapter
import com.rabbithole.musicbbit.service.playback.MusicNotificationPort
import com.rabbithole.musicbbit.service.playback.PlayerPort
import com.rabbithole.musicbbit.service.playback.PlaybackSession
import com.rabbithole.musicbbit.service.playback.ServiceStarter
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

    @Binds
    @Singleton
    abstract fun bindAudioFocusPort(impl: AudioFocusManager): AudioFocusPort

    @Binds
    @Singleton
    abstract fun bindMusicNotificationPort(impl: MusicNotificationManager): MusicNotificationPort

    @Binds
    @Singleton
    abstract fun bindServiceStarter(impl: AndroidServiceStarter): ServiceStarter
}

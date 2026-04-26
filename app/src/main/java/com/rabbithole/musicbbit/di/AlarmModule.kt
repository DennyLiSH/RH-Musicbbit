package com.rabbithole.musicbbit.di

import com.rabbithole.musicbbit.service.AlarmVolumeController
import com.rabbithole.musicbbit.service.alarm.Clock
import com.rabbithole.musicbbit.service.alarm.SystemClock
import com.rabbithole.musicbbit.service.alarm.ports.AndroidNotificationAdapter
import com.rabbithole.musicbbit.service.alarm.ports.AndroidWakeLockAdapter
import com.rabbithole.musicbbit.service.alarm.ports.NotificationPort
import com.rabbithole.musicbbit.service.alarm.ports.VolumeRampPort
import com.rabbithole.musicbbit.service.alarm.ports.WakeLockPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for alarm-fire orchestration ports. All ports are SingletonComponent-scoped
 * so they can be shared between MusicPlaybackService and the future AlarmFireSession.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmModule {

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock

    @Binds
    @Singleton
    abstract fun bindWakeLockPort(impl: AndroidWakeLockAdapter): WakeLockPort

    @Binds
    @Singleton
    abstract fun bindNotificationPort(impl: AndroidNotificationAdapter): NotificationPort

    @Binds
    @Singleton
    abstract fun bindVolumeRampPort(impl: AlarmVolumeController): VolumeRampPort
}

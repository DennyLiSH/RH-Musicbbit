package com.rabbithole.musicbbit

import android.app.Application
import com.rabbithole.musicbbit.data.local.MediaStoreObserver
import com.rabbithole.musicbbit.service.alarm.AlarmStartupReconciler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MusicApplication : Application() {

    @Inject
    lateinit var mediaStoreObserver: MediaStoreObserver

    @Inject
    lateinit var alarmStartupReconciler: AlarmStartupReconciler

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        // MediaStoreObserver auto-registers in its init block when injected
        alarmStartupReconciler.reconcile()
    }
}

package com.rabbithole.musicbbit

import android.app.Application
import com.rabbithole.musicbbit.data.local.MediaStoreObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MusicApplication : Application() {

    @Inject
    lateinit var mediaStoreObserver: MediaStoreObserver

    @Inject
    lateinit var alarmStartupReconciler: AlarmStartupReconciler

    override fun onCreate() {
        super.onCreate()
        // MediaStoreObserver auto-registers in its init block when injected
        alarmStartupReconciler.reconcile()
    }
}

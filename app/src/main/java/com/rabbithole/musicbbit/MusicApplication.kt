package com.rabbithole.musicbbit

import android.app.Application
import android.content.pm.ApplicationInfo
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
        LocaleHelper.applyOnStartup(this)
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        // MediaStoreObserver auto-registers in its init block when injected
        alarmStartupReconciler.reconcile()
    }
}

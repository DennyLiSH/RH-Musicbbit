package com.rabbithole.musicbbit

import android.util.Log
import timber.log.Timber

/**
 * Release build logging tree that forwards ERROR and WARN to the system log
 * and silently drops DEBUG/INFO to avoid log spam in production.
 */
class ReleaseTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.WARN) return
        Log.println(priority, tag, message)
        t?.let { Log.println(priority, tag, Log.getStackTraceString(it)) }
    }
}

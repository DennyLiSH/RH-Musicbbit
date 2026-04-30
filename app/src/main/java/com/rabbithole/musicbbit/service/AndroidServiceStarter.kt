package com.rabbithole.musicbbit.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rabbithole.musicbbit.service.playback.ServiceStarter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
) : ServiceStarter {
    override fun startService() {
        val intent = Intent(context, MusicPlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stopService() {
        val intent = Intent(context, MusicPlaybackService::class.java)
        context.stopService(intent)
    }
}

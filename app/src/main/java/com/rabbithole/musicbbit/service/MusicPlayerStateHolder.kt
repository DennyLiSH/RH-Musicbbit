package com.rabbithole.musicbbit.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.rabbithole.musicbbit.di.ApplicationScope
import com.rabbithole.musicbbit.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt singleton that bridges [MusicPlaybackService] and the UI layer.
 *
 * Manages service binding/unbinding and exposes playback state as a [StateFlow].
 * All playback control calls are forwarded to the bound service.
 */
@Singleton
class MusicPlayerStateHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var service: MusicPlaybackService? = null
    private var isBound = false
    private var collectionJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val musicBinder = binder as? MusicPlaybackService.MusicBinder
            val svc = musicBinder?.getService() ?: return
            service = svc
            isBound = true
            collectionJob = appScope.launch {
                svc.playbackState.collect { state ->
                    _playbackState.value = state
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            collectionJob?.cancel()
            collectionJob = null
            service = null
            isBound = false
        }
    }

    fun bindService() {
        if (isBound) return
        val intent = Intent(context, MusicPlaybackService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            service = null
        }
    }

    fun play(song: Song, playlistId: Long = -1) {
        ensureServiceRunning()
        service?.play(song, playlistId)
    }

    fun playPlaylist(songs: List<Song>, startIndex: Int, playlistId: Long) {
        ensureServiceRunning()
        service?.playQueue(songs, startIndex, playlistId)
    }

    fun pause() {
        service?.pause()
    }

    fun resume() {
        service?.resume()
    }

    fun next() {
        service?.next()
    }

    fun previous() {
        service?.previous()
    }

    fun seekTo(positionMs: Long) {
        service?.seekTo(positionMs)
    }

    fun setPlayMode(mode: PlayMode) {
        service?.setPlayMode(mode)
    }

    fun stop() {
        service?.stop()
    }

    private fun ensureServiceRunning() {
        val intent = Intent(context, MusicPlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
        if (!isBound) {
            bindService()
        }
    }
}

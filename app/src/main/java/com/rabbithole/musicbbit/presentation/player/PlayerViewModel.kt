package com.rabbithole.musicbbit.presentation.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.MusicPlaybackService
import com.rabbithole.musicbbit.service.PlayMode
import com.rabbithole.musicbbit.service.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRepository: AlarmRepository,
) : ViewModel() {

    data class PlayerUiState(
        val errorMessageResId: Int? = null
    )

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    internal val _playbackState = MutableStateFlow(PlaybackState())
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
            collectionJob = viewModelScope.launch {
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

    init {
        bindService()
    }

    private fun bindService() {
        if (isBound) return
        val intent = Intent(context, MusicPlaybackService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindService() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            service = null
        }
    }

    override fun onCleared() {
        unbindService()
        super.onCleared()
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

    val alarmLabel: StateFlow<String?> = playbackState
        .map { it.alarmId }
        .distinctUntilChanged()
        .flatMapLatest { alarmId ->
            flow {
                val label = if (alarmId != null) {
                    runCatching {
                        alarmRepository.getAlarmById(alarmId)?.label
                    }.getOrElse { e ->
                        Timber.e(e, "Failed to load alarm label for id=$alarmId")
                        null
                    }
                } else {
                    null
                }
                emit(label)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

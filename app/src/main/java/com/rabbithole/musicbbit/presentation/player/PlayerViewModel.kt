package com.rabbithole.musicbbit.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.PlayMode
import com.rabbithole.musicbbit.service.PlaybackState
import com.rabbithole.musicbbit.service.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val alarmRepository: AlarmRepository,
) : ViewModel() {

    data class PlayerUiState(
        val errorMessageResId: Int? = null
    )

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackState())

    init {
        playbackController.playerEvents
            .onEach { event ->
                Timber.d("PlayerEvent: $event")
            }
            .launchIn(viewModelScope)
    }

    fun play(song: Song, playlistId: Long = -1) {
        playbackController.play(song, playlistId)
    }

    fun playPlaylist(songs: List<Song>, startIndex: Int, playlistId: Long) {
        playbackController.playQueue(songs, startIndex, playlistId)
    }

    fun pause() {
        playbackController.pause()
    }

    fun resume() {
        playbackController.resume()
    }

    fun next() {
        playbackController.next()
    }

    fun previous() {
        playbackController.previous()
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun setPlayMode(mode: PlayMode) {
        playbackController.setPlayMode(mode)
    }

    fun stop() {
        playbackController.stop()
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
        .catch { e ->
            Timber.e(e, "Alarm label flow failed")
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

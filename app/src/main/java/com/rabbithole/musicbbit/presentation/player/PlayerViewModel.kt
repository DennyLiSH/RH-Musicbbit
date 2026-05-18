package com.rabbithole.musicbbit.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.PlayMode
import com.rabbithole.musicbbit.service.PlaybackState
import com.rabbithole.musicbbit.service.playback.PlaybackSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

/**
 * Thin UI facade over [PlaybackSession].
 *
 * All playback control methods are pass-throughs. The only value this ViewModel adds
 * is lifecycle-scoped state collection via [stateIn] so that Composables get an
 * eager, replayed [StateFlow] without extra boilerplate.
 *
 * TODO: Consider eliminating this module entirely and letting Screens inject
 *       [PlaybackSession] directly once a clean Compose injection pattern is in place.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackSession,
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackState())

    val alarmLabel: StateFlow<String?> = playbackState
        .map { it.alarmLabel }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        Timber.d("PlayerViewModel created")
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
}

package com.rabbithole.musicbbit.service

import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.service.playback.PlayerPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages periodic playback progress saving and position ticking.
 *
 * Runs within the provided [CoroutineScope] (typically the service scope).
 */
class PlaybackProgressTracker(
    private val scope: CoroutineScope,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val playerPort: PlayerPort,
    private val getState: () -> PlaybackState,
) {
    private var progressSaveJob: Job? = null
    private var progressTickJob: Job? = null

    fun startSaveLoop(intervalMs: Long) {
        progressSaveJob?.cancel()
        progressSaveJob = scope.launch {
            while (true) {
                delay(intervalMs)
                saveProgress()
            }
        }
    }

    fun stopSaveLoop() {
        progressSaveJob?.cancel()
    }

    fun startTickLoop(intervalMs: Long, onPositionUpdate: (Long) -> Unit) {
        progressTickJob?.cancel()
        progressTickJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                val state = getState()
                if (state.isPlaying) {
                    onPositionUpdate(playerPort.currentPositionMs())
                }
            }
        }
    }

    fun stopTickLoop() {
        progressTickJob?.cancel()
        progressTickJob = null
    }

    fun saveProgress() {
        val state = getState()
        val song = state.currentSong ?: return
        val position = playerPort.currentPositionMs()

        scope.launch {
            val progress = PlaybackProgress(
                songId = song.id,
                positionMs = position,
                updatedAt = System.currentTimeMillis(),
                playlistId = state.currentPlaylistId
            )
            val result = playbackProgressRepository.saveProgress(progress)
            result.onSuccess {
                Timber.d("Progress saved: songId=${song.id}, position=$position")
            }.onFailure { error ->
                Timber.e(error, "Failed to save playback progress")
            }
        }
    }
}

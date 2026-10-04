package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.di.MainDispatcher
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.service.PlayMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Deep module that encapsulates all user playback logic extracted from [MusicPlaybackService].
 *
 * Responsibilities:
 *   - Managing [PlaybackState] via a [StateFlow]
 *   - Driving [PlayerPort] (queue, play, pause, seek, etc.)
 *   - Handling [PlayerEvent]s and reflecting them into state
 *   - Coordinating audio focus, service lifecycle, and progress tracking
 *
 * State/event/progress/focus machinery lives in [SessionCore]; this class adds the
 * user-side queue entry points (progress restore, shuffle/repeat) and stops immediately
 * when the queue ends.
 *
 * All public entry points are no-ops while another session (alarm) owns the
 * shared player — see [SessionCore.issueCommand]. It does **not** interact with Android Service specifics such as
 * [startForeground] / [stopForeground]; those remain in [MusicPlaybackService].
 *
 * Events and audio-focus callbacks are routed through [PlaybackCoordinator] so that
 * [AlarmPlaybackSession] can share the same [PlayerPort] without interference.
 */
@Singleton
class UserPlaybackSession @Inject constructor(
    private val playerPort: PlayerPort,
    private val audioStreamPort: AudioStreamPort,
    playbackProgressRepository: PlaybackProgressRepository,
    serviceStarter: ServiceStarter,
    audioFocusPort: AudioFocusPort,
    playbackCoordinator: PlaybackCoordinator,
    @MainDispatcher mainDispatcher: CoroutineDispatcher,
) : SessionCore(
    playerPort = playerPort,
    playbackProgressRepository = playbackProgressRepository,
    audioFocusPort = audioFocusPort,
    serviceStarter = serviceStarter,
    playbackCoordinator = playbackCoordinator,
    mainDispatcher = mainDispatcher,
    syncPositionOnPlayStart = true,
) {

    val playerEvents: SharedFlow<PlayerEvent> = playerPort.events

    /** True while another session (the alarm) owns the shared player — UI should disable controls. */
    val commandsBlocked: StateFlow<Boolean> = playbackCoordinator.activeConsumer
        .map { it != null && it !== this }
        .stateIn(sessionScope, SharingStarted.Eagerly, initialValue = false)

    init {
        Timber.i("UserPlaybackSession created")
    }

    // -------- Public playback API --------------------------------------------

    fun play(song: Song, playlistId: Long) {
        issueCommand {
            if (!audioFocusPort.requestFocus()) {
                Timber.w("Failed to gain audio focus")
                return@issueCommand
            }
            Timber.i("Playing single song: ${song.title}, playlistId=$playlistId")

            playbackCoordinator.activate(this@UserPlaybackSession)
            audioStreamPort.setAlarmStream(false)
            serviceStarter.startService()

            playerPort.setQueue(
                items = listOf(PlayItem(uri = song.path, tag = song)),
                startIndex = 0,
                startPositionMs = 0,
            )
            playerPort.play()

            applyPlaybackState(
                song = song,
                playlistId = playlistId,
                queue = listOf(song),
                queueIndex = 0,
            )
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int, playlistId: Long) {
        issueCommand {
            if (songs.isEmpty()) {
                Timber.w("playQueue called with empty list")
                return@issueCommand
            }
            if (!audioFocusPort.requestFocus()) {
                Timber.w("Failed to gain audio focus")
                return@issueCommand
            }
            val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
            val startSong = songs[safeIndex]

            Timber.i(
                "Playing queue of ${songs.size} songs, startIndex=$safeIndex, playlistId=$playlistId"
            )

            playbackCoordinator.activate(this@UserPlaybackSession)
            audioStreamPort.setAlarmStream(false)
            serviceStarter.startService()

            val mediaItems = songs.map { song ->
                PlayItem(uri = song.path, tag = song)
            }

            playerPort.setQueue(items = mediaItems, startIndex = safeIndex, startPositionMs = 0)

            sessionScope.launch {
                val progressResult = playbackProgressRepository.getProgress(startSong.id, playlistId)
                // Re-check ownership: the alarm may have taken the player while we were
                // suspended on IO. Seeking/playing now would act on the alarm's queue.
                if (playbackCoordinator.isOwnedByAnother(this@UserPlaybackSession)) {
                    Timber.w("playQueue aborted after suspension: another session owns the player")
                    return@launch
                }
                progressResult.getOrNull()?.let { progress ->
                    Timber.i("Restoring progress for song ${startSong.id}: ${progress.positionMs}ms")
                    playerPort.seekTo(progress.positionMs)
                }
                playerPort.play()
            }

            applyPlaybackState(
                song = startSong,
                playlistId = playlistId,
                queue = songs,
                queueIndex = safeIndex,
            )
        }
    }

    fun next() {
        issueCommand {
            Timber.i("Skipping to next")
            if (playerPort.hasNext()) {
                progressTracker.saveProgress()
                playerPort.next()
            } else {
                Timber.d("No next media item")
            }
        }
    }

    fun previous() {
        issueCommand {
            Timber.i("Skipping to previous")
            if (playerPort.hasPrevious()) {
                progressTracker.saveProgress()
                playerPort.previous()
            } else {
                Timber.d("No previous media item")
            }
        }
    }

    /** Toggle play/pause based on the current state. Used by external action handlers (e.g. notification). */
    fun togglePlayPause() {
        issueCommand {
            if (playbackState.value.isPlaying) pause() else resume()
        }
    }

    fun stop() {
        issueCommand {
            coreStop(skipSave = false)
        }
    }

    fun setPlayMode(mode: PlayMode) {
        issueCommand {
            Timber.i("Setting play mode: $mode")
            playerPort.setShuffleEnabled(mode == PlayMode.RANDOM)
            playerPort.setRepeatMode(
                when (mode) {
                    PlayMode.REPEAT_ONE -> PlayerRepeatMode.ONE
                    else -> PlayerRepeatMode.OFF
                }
            )
            updateState { it.copy(playMode = mode) }
        }
    }

    // pause/resume/seekTo guards are inherited from SessionCore.issueCommand.

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun applyPlaybackState(
        song: Song,
        playlistId: Long,
        queue: List<Song>,
        queueIndex: Int,
    ) {
        updateState {
            it.copy(
                currentSong = song,
                currentPlaylistId = playlistId,
                queue = queue,
                queueIndex = queueIndex,
                positionMs = 0,
                durationMs = song.durationMs,
            )
        }
    }

    override fun handleQueueEnded() {
        Timber.i("Queue ended, stopping playback")
        stop()
    }
}
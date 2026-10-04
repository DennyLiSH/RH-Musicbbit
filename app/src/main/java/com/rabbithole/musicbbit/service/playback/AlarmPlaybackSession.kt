package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.di.MainDispatcher
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Deep module that owns alarm playback.
 *
 * Logically independent from [UserPlaybackSession] but shares the single [PlayerPort] instance.
 * Events are routed through [PlaybackCoordinator] so only the active session reacts.
 *
 * State/event/progress/focus machinery lives in [SessionCore]; this class adds the alarm
 * queue entry points (alarm-stream routing) and the queue-ended
 * policy: the stop is deferred via [stopDeferred] and the final save is suppressed so the
 * just-finished song's end position is not written.
 */
@Singleton
class AlarmPlaybackSession @Inject constructor(
    private val playerPort: PlayerPort,
    audioStreamPort: AudioStreamPort,
    playbackProgressRepository: PlaybackProgressRepository,
    serviceStarter: ServiceStarter,
    audioFocusPort: AudioFocusPort,
    playbackCoordinator: PlaybackCoordinator,
    @MainDispatcher mainDispatcher: CoroutineDispatcher,
) : SessionCore(
    playerPort = playerPort,
    playbackProgressRepository = playbackProgressRepository,
    audioFocusPort = audioFocusPort,
    audioStreamPort = audioStreamPort,
    serviceStarter = serviceStarter,
    playbackCoordinator = playbackCoordinator,
    mainDispatcher = mainDispatcher,
    syncPositionOnPlayStart = false,
) {

    init {
        Timber.i("AlarmPlaybackSession created")
    }

    /**
     * Start playing [songs] as an alarm queue.
     *
     * @param songs Non-empty list of songs to play.
     * @param startIndex Initial item index, coerced to `[0, songs.lastIndex]`.
     * @param playlistId Playlist identifier. Must be positive.
     * @param useAlarmStream true routes audio through the alarm stream (bypasses silent mode
     *   and most DND filters); false uses the media stream, which DND mutes.
     */
    fun playAlarmQueue(songs: List<Song>, startIndex: Int, playlistId: Long, useAlarmStream: Boolean) {
        if (songs.isEmpty()) {
            Timber.w("playAlarmQueue called with empty list")
            return
        }
        if (playlistId <= 0) {
            Timber.w("playAlarmQueue called with invalid playlistId=$playlistId")
            return
        }
        if (!audioFocusPort.requestFocus()) {
            Timber.w("Failed to gain audio focus for alarm playback")
            return
        }

        playbackCoordinator.activate(this)

        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        val startSong = songs[safeIndex]

        Timber.i(
            "Playing alarm queue of ${songs.size} songs, startIndex=$safeIndex, playlistId=$playlistId"
        )
        audioStreamPort.setAlarmStream(useAlarmStream)
        serviceStarter.startService()

        val mediaItems = songs.map { song ->
            PlayItem(uri = song.path, tag = song)
        }
        playerPort.setQueue(items = mediaItems, startIndex = safeIndex, startPositionMs = 0)
        playerPort.play()

        updateState {
            it.copy(
                currentSong = startSong,
                currentPlaylistId = playlistId,
                queue = songs,
                queueIndex = safeIndex,
                positionMs = 0,
                durationMs = startSong.durationMs,
                isPlaying = true,
            )
        }
        // Note: tick/save loops are started by handleIsPlayingChanged(true) — aligned
        // with UserPlaybackSession. The loops are idempotent (cancel-then-launch) so the
        // IsPlayingChanged event re-starting them is safe.
    }

    fun stop() {
        Timber.i("Stopping alarm playback")
        coreStop(skipSave = false)
    }

    override fun handleQueueEnded() {
        Timber.i("Alarm queue ended")
        val playlistId = playbackState.value.currentPlaylistId
        stopDeferred(PlaybackTransition.QueueEnded(playlistId))
    }
}

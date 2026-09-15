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
 * queue entry points (alarm-stream routing, first-song preload) and the queue-ended
 * policy: the stop is deferred to [com.rabbithole.musicbbit.service.alarm.AlarmFireSession]
 * and the final save is suppressed so the just-finished song's end position is not written.
 */
@Singleton
class AlarmPlaybackSession @Inject constructor(
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
    syncPositionOnPlayStart = false,
) {

    /**
     * Set by [handleQueueEnded] to signal that the subsequent [stop] call (driven by
     * AlarmFireSession upon receiving QueueEnded) should skip saveProgress — at this
     * point the queue has ended naturally and any save would write the just-finished
     * song's end position. Cancels the saveProgress branch in [stop] without changing
     * its `currentSong != null` guard semantics.
     */
    private var queueEndedPending = false

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
        val skipSave = queueEndedPending
        queueEndedPending = false
        coreStop(skipSave = skipSave)
    }

    fun preloadFirstSong(uri: String) {
        playerPort.setQueue(
            items = listOf(PlayItem(uri = uri)),
            startIndex = 0,
            startPositionMs = 0,
        )
        Timber.d("Preloaded first song for alarm: $uri")
    }

    override fun handleQueueEnded() {
        Timber.i("Alarm queue ended")
        val playlistId = playbackState.value.currentPlaylistId
        queueEndedPending = true
        progressTracker.stopSaveLoop()
        progressTracker.stopTickLoop()
        playbackCoordinator.deactivate(this)
        sessionScope.launch {
            // cancelAndAwaitPendingSave MUST complete before emit QueueEnded — ensures
            // AlarmFireSession's deletePlaylistProgressIfMatches runs after any in-flight
            // save completes (or is cancelled). See commit 20daaa5: prevents pending save
            // from racing past delete.
            progressTracker.cancelAndAwaitPendingSave()
            tryEmitTransition(PlaybackTransition.QueueEnded(playlistId))
        }
    }
}

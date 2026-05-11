package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.AutoStop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Controls when an alarm playback should automatically stop.
 *
 * Supports three stopping mechanisms:
 * - **ByMinutes**: stop after a configurable delay
 * - **BySongCount**: stop after N songs have played
 * - **ExtendToEnd**: stop after the current song finishes
 *
 * This module is an internal collaborator of [AlarmFireSession]; it does not
 * interact with the playback layer directly. Instead it returns signals
 * (Boolean / callback) that the session uses to decide when to call stop.
 */
class AutoStopController(private val scope: CoroutineScope) {

    private var autoStopJob: Job? = null
    private var songsRemaining: Int = 0
    private var extendToEnd: Boolean = false

    /** Whether playback should stop after the current song completes. */
    fun isExtendToEnd(): Boolean = extendToEnd

    /** Toggle extend-to-end mode. */
    fun setExtendToEnd(enabled: Boolean) {
        extendToEnd = enabled
        Timber.i("Extend-to-end mode: $enabled")
    }

    /**
     * Start the auto-stop mechanism according to [config].
     *
     * @param config the auto-stop configuration from the alarm
     * @param onTimerTrigger called when the minute-based timer fires
     */
    fun start(config: AutoStop?, onTimerTrigger: () -> Unit) {
        cancel()
        when (config) {
            is AutoStop.ByMinutes -> {
                val delayMs = config.minutes * 60_000L
                Timber.i("Scheduling auto-stop in ${config.minutes} minutes")
                autoStopJob = scope.launch {
                    delay(delayMs)
                    Timber.i("Auto-stop triggered by timer")
                    onTimerTrigger()
                }
            }
            is AutoStop.BySongCount -> {
                songsRemaining = config.count
                Timber.i("Song counter set to ${config.count}")
            }
            null -> {
                // No auto-stop configured
            }
        }
    }

    /**
     * Extend the auto-stop timer. The previous job is cancelled; a fresh delay starts.
     *
     * No-op if no minute-based timer is currently active.
     */
    fun extend(minutes: Int, onTimerTrigger: () -> Unit) {
        if (autoStopJob == null) {
            Timber.d("extend: no auto-stop timer in flight, ignoring")
            return
        }
        start(AutoStop.ByMinutes(minutes), onTimerTrigger)
        Timber.i("Auto-stop extended by $minutes minutes")
    }

    /** Cancel the auto-stop timer and reset the song counter. */
    fun cancel() {
        autoStopJob?.cancel()
        autoStopJob = null
        songsRemaining = 0
        Timber.d("Auto-stop cancelled")
    }

    /**
     * Called when a song completes naturally (auto-advance).
     *
     * Decrements the song counter; when it reaches zero, returns **true** to signal
     * that the caller should stop playback.
     */
    fun onSongCompleted(): Boolean {
        if (songsRemaining <= 0) return false
        songsRemaining--
        Timber.d("Song completed, songsRemaining=$songsRemaining")
        return if (songsRemaining <= 0) {
            Timber.i("Song counter reached zero")
            true
        } else {
            false
        }
    }

    /**
     * Called when the queue ends before the song counter reaches zero.
     *
     * Returns **true** if a song counter is active and the caller should stop playback.
     */
    fun onQueueEnded(): Boolean {
        return if (songsRemaining > 0) {
            Timber.i("Queue ended with songsRemaining=$songsRemaining")
            true
        } else {
            false
        }
    }

    /** Reset all state. Called when playback is fully stopped or on error. */
    fun reset() {
        cancel()
        extendToEnd = false
        Timber.d("AutoStopController reset")
    }
}

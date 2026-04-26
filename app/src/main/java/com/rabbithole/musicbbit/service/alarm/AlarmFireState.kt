package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Song

/**
 * Observable state of [AlarmFireSession]. UI consumers subscribe via the session's StateFlow.
 *
 * The session is a singleton, so at most one alarm-fire is active at a time. Firing a new
 * alarm while another is active stops the prior one before transitioning.
 */
sealed class AlarmFireState {

    /** No alarm is firing. */
    data object Idle : AlarmFireState()

    /** Alarm intent received; loading alarm + playlist + progress. */
    data class Loading(val alarmId: Long) : AlarmFireState()

    /** Playback driven by the alarm is active. */
    data class Playing(
        val alarmId: Long,
        val currentSong: Song?,
        val positionMs: Long = 0L,
        val autoStopRemainingMs: Long? = null,
        val isExtended: Boolean = false,
    ) : AlarmFireState()

    /** Playback was paused by user action; alarm session is still alive. */
    data class Paused(
        val alarmId: Long,
        val currentSong: Song?,
        val positionMs: Long,
    ) : AlarmFireState()

    /** Playback ended (autoStop, user stop, or playback-finished). */
    data object Stopped : AlarmFireState()

    /** Loading or firing failed. The session is parked until the next fire(). */
    data class Error(val alarmId: Long, val message: String) : AlarmFireState()

    /** The id of the alarm this state describes, or `null` for [Idle] / [Stopped]. */
    val alarmIdOrNull: Long?
        get() = when (this) {
            is Loading -> alarmId
            is Playing -> alarmId
            is Paused -> alarmId
            is Error -> alarmId
            Idle, Stopped -> null
        }
}

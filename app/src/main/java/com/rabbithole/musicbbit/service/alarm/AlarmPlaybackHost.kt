package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Song

/**
 * Bridge that lets [AlarmFireSession] drive Android-side playback without depending on
 * [com.rabbithole.musicbbit.service.MusicPlaybackService] directly.
 *
 * The session is pure Kotlin and exchanges only String URIs over this seam; the host
 * (the Service) converts to [android.net.Uri] internally if needed. The session uses
 * this seam for actions that still require Service / AudioFocus scoping (audio-focus
 * request, foreground notification continuity).
 */
interface AlarmPlaybackHost {

    /** Pre-warm the player with the first song's URI to reduce alarm-time latency. */
    fun preloadFirstSong(uri: String)

    /** Drive the queue + start position used by the alarm. The host updates its own UI state. */
    fun playAlarmQueue(songs: List<Song>, startIndex: Int, playlistId: Long, alarmId: Long)

    /** Stop playback (used by the auto-stop timer and external stop()). */
    fun stopPlayback()
}

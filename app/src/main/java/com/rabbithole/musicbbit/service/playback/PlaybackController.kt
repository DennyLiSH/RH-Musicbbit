package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.PlayMode
import com.rabbithole.musicbbit.service.PlaybackState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {
    // 普通播放命令
    fun play(song: Song, playlistId: Long)
    fun playQueue(songs: List<Song>, startIndex: Int, playlistId: Long)
    fun pause()
    fun resume()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun stop()
    fun setPlayMode(mode: PlayMode)

    // 闹钟播放命令
    fun playAlarmQueue(songs: List<Song>, startIndex: Int, playlistId: Long, alarmId: Long)
    fun preloadFirstSong(uri: String)

    // 状态与事件观察
    val playbackState: StateFlow<PlaybackState>
    val playerEvents: SharedFlow<PlayerEvent>
}

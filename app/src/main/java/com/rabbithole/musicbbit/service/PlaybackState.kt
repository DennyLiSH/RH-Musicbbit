package com.rabbithole.musicbbit.service

import com.rabbithole.musicbbit.domain.model.Song

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentSong: Song? = null,
    val currentPlaylistId: Long? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = 0
)

enum class PlayMode {
    SEQUENTIAL, RANDOM, REPEAT_ONE
}

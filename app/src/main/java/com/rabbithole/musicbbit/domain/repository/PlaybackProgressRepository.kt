package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.PlaybackProgress

interface PlaybackProgressRepository {
    suspend fun saveProgress(progress: PlaybackProgress)
    suspend fun getProgress(songId: Long, playlistId: Long?): PlaybackProgress?
    suspend fun deleteProgress(songId: Long, playlistId: Long?)
    suspend fun deleteAllProgressForPlaylist(playlistId: Long)
}

package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.PlaybackProgress

interface PlaybackProgressRepository {
    suspend fun saveProgress(progress: PlaybackProgress): Result<Unit>
    suspend fun getProgress(songId: Long, playlistId: Long?): Result<PlaybackProgress?>
    suspend fun deleteProgress(songId: Long, playlistId: Long?): Result<Unit>
    suspend fun deleteAllProgressForPlaylist(playlistId: Long): Result<Unit>
}

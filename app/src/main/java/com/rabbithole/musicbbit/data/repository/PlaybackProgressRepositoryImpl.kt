package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.PlaybackProgressDao
import com.rabbithole.musicbbit.data.model.PlaybackProgressEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlaybackProgressRepositoryImpl @Inject constructor(
    private val playbackProgressDao: PlaybackProgressDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PlaybackProgressRepository {

    override suspend fun saveProgress(progress: PlaybackProgress): Result<Unit> = withContext(ioDispatcher) {
        try {
            playbackProgressDao.insert(progress.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getProgress(songId: Long, playlistId: Long?): Result<PlaybackProgress?> = withContext(ioDispatcher) {
        try {
            val entity = playbackProgressDao.getBySongIdAndPlaylistId(songId, playlistId)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteProgress(songId: Long, playlistId: Long?): Result<Unit> = withContext(ioDispatcher) {
        try {
            playbackProgressDao.deleteBySongIdAndPlaylistId(songId, playlistId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllProgressForPlaylist(playlistId: Long): Result<Unit> = withContext(ioDispatcher) {
        try {
            playbackProgressDao.deleteByPlaylistId(playlistId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun PlaybackProgress.toEntity(): PlaybackProgressEntity {
        return PlaybackProgressEntity(
            songId = songId,
            positionMs = positionMs,
            updatedAt = updatedAt,
            playlistId = playlistId
        )
    }

    private fun PlaybackProgressEntity.toDomain(): PlaybackProgress {
        return PlaybackProgress(
            songId = songId,
            positionMs = positionMs,
            updatedAt = updatedAt,
            playlistId = playlistId
        )
    }
}

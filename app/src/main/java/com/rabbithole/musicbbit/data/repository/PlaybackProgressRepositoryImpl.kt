package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.data.local.dao.PlaybackProgressDao
import com.rabbithole.musicbbit.data.mapper.toDomain
import com.rabbithole.musicbbit.data.mapper.toEntity
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class PlaybackProgressRepositoryImpl @Inject constructor(
    private val playbackProgressDao: PlaybackProgressDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PlaybackProgressRepository {

    override suspend fun saveProgress(progress: PlaybackProgress): Result<Unit> = withContext(ioDispatcher) {
        try {
            playbackProgressDao.insert(progress.toEntity())
            Timber.d(
                "Progress saved: songId=${progress.songId}, playlistId=${progress.playlistId}, " +
                    "position=${progress.positionMs}ms"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save progress: songId=${progress.songId}")
            Result.failure(e)
        }
    }

    override suspend fun getProgress(songId: Long, playlistId: Long): Result<PlaybackProgress?> = withContext(ioDispatcher) {
        try {
            val progress = playbackProgressDao.getBySongIdAndPlaylistId(songId, playlistId)?.toDomain()
            Timber.d("Progress loaded: songId=$songId, playlistId=$playlistId, found=${progress != null}")
            Result.success(progress)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get progress: songId=$songId, playlistId=$playlistId")
            Result.failure(e)
        }
    }

    override suspend fun deleteProgress(songId: Long, playlistId: Long): Result<Unit> = withContext(ioDispatcher) {
        try {
            playbackProgressDao.deleteBySongIdAndPlaylistId(songId, playlistId)
            Timber.d("Progress deleted: songId=$songId, playlistId=$playlistId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete progress: songId=$songId, playlistId=$playlistId")
            Result.failure(e)
        }
    }

    override suspend fun deleteAllProgressForPlaylist(playlistId: Long): Result<Unit> = withContext(ioDispatcher) {
        try {
            playbackProgressDao.deleteByPlaylistId(playlistId)
            Timber.i("All progress deleted for playlist: playlistId=$playlistId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete progress for playlist: playlistId=$playlistId")
            Result.failure(e)
        }
    }

    override suspend fun getProgressForPlaylist(playlistId: Long): Result<List<PlaybackProgress>> = withContext(ioDispatcher) {
        try {
            val progressList = playbackProgressDao.getByPlaylistId(playlistId).map { it.toDomain() }
            Timber.d("Progress list loaded: playlistId=$playlistId, count=${progressList.size}")
            Result.success(progressList)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get progress list: playlistId=$playlistId")
            Result.failure(e)
        }
    }
}

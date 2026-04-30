package com.rabbithole.musicbbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rabbithole.musicbbit.domain.model.PlaybackProgress

@Dao
interface PlaybackProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: PlaybackProgress)

    @Query("SELECT * FROM playback_progress WHERE songId = :songId AND playlistId = :playlistId")
    suspend fun getBySongIdAndPlaylistId(songId: Long, playlistId: Long): PlaybackProgress?

    @Query("DELETE FROM playback_progress WHERE songId = :songId AND playlistId = :playlistId")
    suspend fun deleteBySongIdAndPlaylistId(songId: Long, playlistId: Long)

    @Query("DELETE FROM playback_progress WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylistId(playlistId: Long)

    @Query("DELETE FROM playback_progress")
    suspend fun deleteAll()

    @Query("SELECT * FROM playback_progress WHERE playlistId = :playlistId ORDER BY updatedAt DESC")
    suspend fun getByPlaylistId(playlistId: Long): List<PlaybackProgress>
}

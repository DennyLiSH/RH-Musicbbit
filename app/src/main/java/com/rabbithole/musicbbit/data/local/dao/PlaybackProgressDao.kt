package com.rabbithole.musicbbit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rabbithole.musicbbit.data.model.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: PlaybackProgressEntity)

    @Delete
    suspend fun delete(progress: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE songId = :songId")
    suspend fun getBySongId(songId: Long): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE songId = :songId")
    fun getBySongIdFlow(songId: Long): Flow<PlaybackProgressEntity?>

    @Query("DELETE FROM playback_progress")
    suspend fun deleteAll()
}

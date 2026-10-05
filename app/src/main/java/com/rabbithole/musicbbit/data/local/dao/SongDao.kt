package com.rabbithole.musicbbit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rabbithole.musicbbit.data.local.model.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>): List<Long>

    @Update
    suspend fun updateAll(songs: List<SongEntity>)

    @Delete
    suspend fun delete(song: SongEntity)

    @Delete
    suspend fun deleteAllSongs(songs: List<SongEntity>)

    @Query("SELECT * FROM songs")
    fun getAll(): Flow<List<SongEntity>>

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' COLLATE NOCASE OR artist LIKE '%' || :query || '%' COLLATE NOCASE")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE path LIKE :prefix || '/%' OR path = :prefix")
    fun getByPathPrefix(prefix: String): Flow<List<SongEntity>>
}

package com.rabbithole.musicbbit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistSongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlistSong: PlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(playlistSongs: List<PlaylistSongEntity>)

    @Delete
    suspend fun delete(playlistSong: PlaylistSongEntity)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun getByPlaylistId(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylistId(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deleteByPlaylistAndSong(playlistId: Long, songId: Long)
}

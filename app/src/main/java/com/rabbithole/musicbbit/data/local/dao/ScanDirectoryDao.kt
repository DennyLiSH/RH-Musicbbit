package com.rabbithole.musicbbit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.rabbithole.musicbbit.data.model.ScanDirectoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDirectoryDao {

    @Insert
    suspend fun insert(directory: ScanDirectoryEntity): Long

    @Delete
    suspend fun delete(directory: ScanDirectoryEntity)

    @Query("SELECT * FROM scan_directories")
    fun getAll(): Flow<List<ScanDirectoryEntity>>

    @Query("SELECT * FROM scan_directories WHERE id = :id")
    suspend fun getById(id: Long): ScanDirectoryEntity?
}

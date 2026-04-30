package com.rabbithole.musicbbit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDirectoryDao {

    @Insert
    suspend fun insert(directory: ScanDirectory): Long

    @Delete
    suspend fun delete(directory: ScanDirectory)

    @Query("SELECT * FROM scan_directories")
    fun getAll(): Flow<List<ScanDirectory>>

    @Query("SELECT * FROM scan_directories WHERE id = :id")
    suspend fun getById(id: Long): ScanDirectory?
}

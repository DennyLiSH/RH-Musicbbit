package com.rabbithole.musicbbit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_directories")
data class ScanDirectoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis()
)

package com.rabbithole.musicbbit.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_directories")
data class ScanDirectory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis()
)

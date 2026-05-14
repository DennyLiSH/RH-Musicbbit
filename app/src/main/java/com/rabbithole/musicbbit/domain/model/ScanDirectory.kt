package com.rabbithole.musicbbit.domain.model

data class ScanDirectory(
    val id: Long = 0,
    val path: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis()
)

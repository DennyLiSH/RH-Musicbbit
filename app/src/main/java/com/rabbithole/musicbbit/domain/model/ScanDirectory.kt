package com.rabbithole.musicbbit.domain.model

data class ScanDirectory(
    val id: Long,
    val path: String,
    val name: String,
    val addedAt: Long
)

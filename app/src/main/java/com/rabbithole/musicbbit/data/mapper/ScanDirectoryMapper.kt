package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.local.model.ScanDirectoryEntity
import com.rabbithole.musicbbit.domain.model.ScanDirectory

fun ScanDirectoryEntity.toDomain() = ScanDirectory(id, path, name, addedAt)
fun ScanDirectory.toEntity() = ScanDirectoryEntity(id, path, name, addedAt)

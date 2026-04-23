package com.rabbithole.musicbbit.domain.repository

import com.rabbithole.musicbbit.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun getAllSongs(): Flow<List<Song>>
    fun searchSongs(query: String): Flow<List<Song>>
    suspend fun refreshSongs(): Result<Unit>
}

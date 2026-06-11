package com.rabbithole.musicbbit.data.local.sync

import com.rabbithole.musicbbit.data.local.model.SongEntity

class SongSyncEngine {
    fun sync(existing: List<SongEntity>, scanned: List<SongEntity>): SongDiff {
        val existingMap = existing.associateBy { it.path }
        val scannedMap = scanned.associateBy { it.path }

        val inserts = scannedMap.filterKeys { it !in existingMap }.values.toList()
        val deletes = existingMap.filterKeys { it !in scannedMap }.values.toList()
        val updates = scannedMap.filterKeys { it in existingMap }
            .mapNotNull { (path, scannedSong) ->
                val existingSong = existingMap[path]!!
                if (scannedSong.copy(id = existingSong.id) != existingSong) {
                    scannedSong.copy(id = existingSong.id)
                } else null
            }

        return SongDiff(inserts, deletes, updates)
    }
}

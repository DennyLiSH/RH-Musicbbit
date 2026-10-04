package com.rabbithole.musicbbit.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 10 → 11: playback_progress gains FK CASCADE to songs/playlists so progress rows
 * can never outlive their parents (delete playlist / remove song / file-sync delete
 * all used to leak orphans). Orphaned rows are purged BEFORE the table is recreated —
 * the new FKs would reject them at insert time. playlistId = -1 rows (legacy ad-hoc
 * playback, write-only dead data per the domain rule in PlaybackProgressTracker)
 * are intentionally removed by the same purge.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM playback_progress WHERE songId NOT IN (SELECT id FROM songs)")
        db.execSQL("DELETE FROM playback_progress WHERE playlistId NOT IN (SELECT id FROM playlists)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playback_progress_new (
                songId INTEGER NOT NULL,
                positionMs INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                playlistId INTEGER NOT NULL,
                PRIMARY KEY(songId, playlistId),
                FOREIGN KEY(songId) REFERENCES songs(id) ON DELETE CASCADE,
                FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO playback_progress_new (songId, positionMs, updatedAt, playlistId) " +
                "SELECT songId, positionMs, updatedAt, playlistId FROM playback_progress"
        )
        db.execSQL("DROP TABLE playback_progress")
        db.execSQL("ALTER TABLE playback_progress_new RENAME TO playback_progress")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_progress_playlistId ON playback_progress(playlistId)")
    }
}
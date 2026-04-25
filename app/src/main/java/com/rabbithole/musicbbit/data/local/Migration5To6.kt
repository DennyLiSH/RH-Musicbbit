package com.rabbithole.musicbbit.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Clean up duplicate songs by path, keeping the one with smallest id
        db.execSQL(
            """
            DELETE FROM songs WHERE id NOT IN (
                SELECT MIN(id) FROM songs GROUP BY path
            )
            """.trimIndent()
        )

        // Create unique index on songs.path
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_songs_path ON songs(path)")

        // Create index on playlists.name
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_name ON playlists(name)")
    }
}

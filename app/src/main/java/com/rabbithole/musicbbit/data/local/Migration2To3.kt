package com.rabbithole.musicbbit.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE playback_progress")
        db.execSQL(
            """
            CREATE TABLE playback_progress (
                songId INTEGER NOT NULL,
                positionMs INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                playlistId INTEGER,
                PRIMARY KEY(songId, playlistId)
            )
            """.trimIndent()
        )
    }
}

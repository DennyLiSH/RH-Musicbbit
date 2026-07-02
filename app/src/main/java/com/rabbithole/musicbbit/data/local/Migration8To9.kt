package com.rabbithole.musicbbit.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add resume-playback flag and ring-mode storage to existing alarms.
        // ALTER TABLE ADD COLUMN runs in a Room transaction; DDL failure rolls back.
        db.execSQL("ALTER TABLE alarms ADD COLUMN resumePlayback INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE alarms ADD COLUMN ringMode TEXT NOT NULL DEFAULT 'NORMAL'")
    }
}

package com.rabbithole.musicbbit.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add per-alarm quiet-mode bypass flag. Default 1 keeps existing alarms on the
        // alarm audio stream (previous unconditional behavior).
        // ALTER TABLE ADD COLUMN runs in a Room transaction; DDL failure rolls back.
        db.execSQL("ALTER TABLE alarms ADD COLUMN ignoreQuietMode INTEGER NOT NULL DEFAULT 1")
    }
}

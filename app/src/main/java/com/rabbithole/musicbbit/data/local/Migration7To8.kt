package com.rabbithole.musicbbit.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new alarms table with autoStop column (replacing autoStopMinutes)
        db.execSQL(
            """
            CREATE TABLE alarms_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                hour INTEGER NOT NULL,
                minute INTEGER NOT NULL,
                repeatDaysBitmask INTEGER NOT NULL,
                excludeHolidays INTEGER NOT NULL DEFAULT 0,
                playlistId INTEGER NOT NULL,
                isEnabled INTEGER NOT NULL,
                label TEXT,
                autoStop TEXT,
                lastTriggeredAt INTEGER
            )
            """.trimIndent()
        )

        // Migrate data: convert autoStopMinutes to autoStop format
        db.execSQL(
            """
            INSERT INTO alarms_new (
                id, hour, minute, repeatDaysBitmask, excludeHolidays,
                playlistId, isEnabled, label, autoStop, lastTriggeredAt
            )
            SELECT
                id, hour, minute, repeatDaysBitmask, excludeHolidays,
                playlistId, isEnabled, label,
                CASE WHEN autoStopMinutes IS NOT NULL THEN 'MINUTES:' || autoStopMinutes ELSE NULL END,
                lastTriggeredAt
            FROM alarms
            """.trimIndent()
        )

        // Drop old table and rename
        db.execSQL("DROP TABLE alarms")
        db.execSQL("ALTER TABLE alarms_new RENAME TO alarms")
    }
}

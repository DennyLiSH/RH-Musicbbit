package com.rabbithole.musicbbit.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE holidays (
                date TEXT NOT NULL PRIMARY KEY,
                year INTEGER NOT NULL,
                name TEXT NOT NULL,
                isHoliday INTEGER NOT NULL DEFAULT 1,
                fetchedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_holidays_year ON holidays(year)")
    }
}

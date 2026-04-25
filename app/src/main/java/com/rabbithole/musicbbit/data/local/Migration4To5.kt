package com.rabbithole.musicbbit.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema change. Built-in holiday data will be available via assets fallback.
    }
}

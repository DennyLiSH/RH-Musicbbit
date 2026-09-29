package com.rabbithole.musicbbit.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import dagger.hilt.android.testing.HiltTestApplication
import org.robolectric.annotation.Config

/**
 * Verifies the 9 → 10 migration adds [ignoreQuietMode] with the correct default
 * and preserves existing rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class Migration9To10Test {

    @Test
    fun `migration adds ignoreQuietMode with default and preserves data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(Version9Callback())
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        db.execSQL(
            """
            INSERT INTO alarms (
                hour, minute, repeatDaysBitmask, excludeHolidays, playlistId,
                isEnabled, label, autoStop, lastTriggeredAt, resumePlayback, ringMode
            ) VALUES (7, 30, 0, 0, 1, 1, 'Test', NULL, NULL, 0, 'FULLSCREEN')
            """.trimIndent()
        )

        MIGRATION_9_10.migrate(db)

        db.query("SELECT label, resumePlayback, ringMode, ignoreQuietMode FROM alarms").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Test", cursor.getString(cursor.getColumnIndexOrThrow("label")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("resumePlayback")))
            assertEquals("FULLSCREEN", cursor.getString(cursor.getColumnIndexOrThrow("ringMode")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("ignoreQuietMode")))
        }

        helper.close()
    }

    private class Version9Callback : SupportSQLiteOpenHelper.Callback(9) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE alarms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    hour INTEGER NOT NULL,
                    minute INTEGER NOT NULL,
                    repeatDaysBitmask INTEGER NOT NULL,
                    excludeHolidays INTEGER NOT NULL DEFAULT 0,
                    playlistId INTEGER NOT NULL,
                    isEnabled INTEGER NOT NULL,
                    label TEXT,
                    autoStop TEXT,
                    lastTriggeredAt INTEGER,
                    resumePlayback INTEGER NOT NULL DEFAULT 1,
                    ringMode TEXT NOT NULL DEFAULT 'NORMAL'
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No-op for this test.
        }
    }
}

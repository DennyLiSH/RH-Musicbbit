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
import org.robolectric.annotation.Config

/**
 * Verifies the 8 → 9 migration adds [resumePlayback] and [ringMode] with correct defaults.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration8To9Test {

    @Test
    fun `migration adds resumePlayback and ringMode with defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(Version8Callback())
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        db.execSQL(
            """
            INSERT INTO alarms (
                hour, minute, repeatDaysBitmask, excludeHolidays, playlistId,
                isEnabled, label, autoStop, lastTriggeredAt
            ) VALUES (7, 30, 0, 0, 1, 1, 'Test', NULL, NULL)
            """.trimIndent()
        )

        MIGRATION_8_9.migrate(db)

        db.query("SELECT resumePlayback, ringMode FROM alarms").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("resumePlayback")))
            assertEquals("NORMAL", cursor.getString(cursor.getColumnIndexOrThrow("ringMode")))
        }

        helper.close()
    }

    private class Version8Callback : SupportSQLiteOpenHelper.Callback(8) {
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
                    lastTriggeredAt INTEGER
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No-op for this test.
        }
    }
}

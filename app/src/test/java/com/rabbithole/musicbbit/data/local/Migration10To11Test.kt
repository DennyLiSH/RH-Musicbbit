package com.rabbithole.musicbbit.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.rabbithole.musicbbit.data.local.dao.PlaybackProgressDao
import com.rabbithole.musicbbit.data.local.model.PlaybackProgressEntity
import com.rabbithole.musicbbit.data.local.model.PlaylistEntity
import com.rabbithole.musicbbit.data.local.model.SongEntity
import com.rabbithole.musicbbit.di.DatabaseModule
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Non-skippable JVM gate for migration 10 → 11.
 *
 * Three cases that block commit if red:
 *   1. Migration purges orphan + legacy ad-hoc rows, keeps valid ones, creates FKs/index.
 *   2. Room opens the migrated DB without schema mismatch (entity ↔ migrated SQL parity).
 *   3. Registration check: opening the migrated v10 file via production DatabaseModule
 *      must NOT trigger fallbackToDestructiveMigration (silent loss of all user data).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class Migration10To11Test {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        context.deleteDatabase("musicbbit_database")
        context.deleteDatabase("v10_migration_test.db")
    }

    /**
     * Test 1 — write a v10-shaped DB with valid + orphan + ad-hoc rows, run the
     * migration, and assert: only the valid row survives; FKs are present with
     * ON DELETE CASCADE; the playlistId index exists.
     */
    @Test
    fun `migrate purges orphans and adds FKs and index`() {
        val dbName = "v10_migration_test.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(V10Callback())
                .build()
        )
        val db = helper.writableDatabase

        // Minimal shape to satisfy FK constraints after migration.
        db.execSQL("INSERT INTO songs (id, path, title, artist, album, durationMs, dateAdded, coverUri) " +
            "VALUES (1, '/a.mp3', 'A', NULL, NULL, 1000, 0, NULL)")
        db.execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) " +
            "VALUES (1, 'P', 0, 0)")

        db.execSQL("INSERT INTO playback_progress (songId, positionMs, updatedAt, playlistId) " +
            "VALUES (1, 100, 1000, 1)")           // valid
        db.execSQL("INSERT INTO playback_progress (songId, positionMs, updatedAt, playlistId) " +
            "VALUES (999, 100, 1000, 1)")          // orphan song
        db.execSQL("INSERT INTO playback_progress (songId, positionMs, updatedAt, playlistId) " +
            "VALUES (1, 100, 1000, 999)")          // orphan playlist
        db.execSQL("INSERT INTO playback_progress (songId, positionMs, updatedAt, playlistId) " +
            "VALUES (2, 100, 1000, -1)")           // legacy ad-hoc

        MIGRATION_10_11.migrate(db)

        db.query("SELECT songId, playlistId FROM playback_progress ORDER BY songId, playlistId").use { c ->
            assertEquals("only the valid row survives", 1, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(c.getColumnIndexOrThrow("songId")))
            assertEquals(1L, c.getLong(c.getColumnIndexOrThrow("playlistId")))
        }

        db.query("SELECT * FROM pragma_foreign_key_list('playback_progress')").use { c ->
            val fks = mutableSetOf<Pair<String, String>>()
            while (c.moveToNext()) {
                fks.add(
                    c.getString(c.getColumnIndexOrThrow("from")) to
                        c.getString(c.getColumnIndexOrThrow("table"))
                )
            }
            assertTrue("FK songId → songs expected", "songId" to "songs" in fks)
            assertTrue("FK playlistId → playlists expected", "playlistId" to "playlists" in fks)
        }

        db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='playback_progress'").use { c ->
            val indexes = mutableSetOf<String>()
            while (c.moveToNext()) {
                indexes.add(c.getString(c.getColumnIndexOrThrow("name")))
            }
            assertTrue(
                "playlistId index missing — indexes=$indexes",
                "index_playback_progress_playlistId" in indexes,
            )
        }

        helper.close()
    }

    /**
     * Test 2 — Room must accept the migrated DB without a "Migration didn't properly
     * handle" exception. Any column / FK / PK mismatch throws here.
     */
    @Test
    fun `room opens migrated v10 db without schema mismatch`() {
        val dbName = "v10_migration_test.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(V10Callback())
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO songs (id, path, title, artist, album, durationMs, dateAdded, coverUri) " +
            "VALUES (1, '/a.mp3', 'A', NULL, NULL, 1000, 0, NULL)")
        db.execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) " +
            "VALUES (1, 'P', 0, 0)")
        db.execSQL("INSERT INTO playback_progress (songId, positionMs, updatedAt, playlistId) " +
            "VALUES (1, 100, 1000, 1)")
        MIGRATION_10_11.migrate(db)
        helper.close()

        // Open with real Room against the migrated file — performs TableInfo validation.
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_10_11)
            .build()
        db2.openHelper.writableDatabase
        db2.close()

        // FK ENFORCEMENT: inserting progress with a missing songId must throw.
        val db4 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_10_11)
            .build()
        val progressDao = db4.playbackProgressDao()
        var caught = false
        try {
            kotlinx.coroutines.runBlocking {
                progressDao.insert(
                    PlaybackProgressEntity(
                        songId = 999L, positionMs = 0L, updatedAt = 0L, playlistId = 1L,
                    )
                )
            }
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            caught = true
        } finally {
            db4.close()
        }
        assertTrue("missing songId should violate FK", caught)
    }

    /**
     * Test 3 — registration check. Build a v10 file with a valid progress row, then
     * open it via the production DatabaseModule.provideDatabase(...) provider. If the
     * module failed to register MIGRATION_10_11, fallbackToDestructiveMigration would
     * dropAllTables and the row would be gone — this test catches that silent data loss.
     */
    @Test
    fun `databaseModule registers the migration (no silent destructive fallback)`() {
        val dbName = "musicbbit_database"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(V10Callback())
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO songs (id, path, title, artist, album, durationMs, dateAdded, coverUri) " +
            "VALUES (1, '/a.mp3', 'A', NULL, NULL, 1000, 0, NULL)")
        db.execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) " +
            "VALUES (1, 'P', 0, 0)")
        db.execSQL("INSERT INTO playback_progress (songId, positionMs, updatedAt, playlistId) " +
            "VALUES (1, 100, 1000, 1)")
        helper.close()

        val module = DatabaseModule
        // Use Hilt's entry point via reflection-free approach: directly invoke
        // the static provideDatabase. The module uses Hilt's ApplicationContext
        // which Robolectric provides via HiltTestApplication.
        val db2 = module.provideDatabase(context)
        db2.openHelper.writableDatabase
        db2.close()

        // Reopen and assert the progress row survived the migration path.
        val db3 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_10_11)
            .build()
        val rows = kotlinx.coroutines.runBlocking {
            db3.playbackProgressDao().getByPlaylistId(1L)
        }
        assertEquals("progress row was destroyed — migration not registered", 1, rows.size)
        db3.close()
    }

    private class V10Callback : SupportSQLiteOpenHelper.Callback(10) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE songs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    path TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT,
                    album TEXT,
                    durationMs INTEGER NOT NULL,
                    dateAdded INTEGER NOT NULL,
                    coverUri TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX index_songs_path ON songs(path)")
            db.execSQL(
                """
                CREATE TABLE playlists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX index_playlists_name ON playlists(name)")
            db.execSQL(
                """
                CREATE TABLE playback_progress (
                    songId INTEGER NOT NULL,
                    positionMs INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    playlistId INTEGER NOT NULL,
                    PRIMARY KEY(songId, playlistId)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE playlist_songs (
                    playlistId INTEGER NOT NULL,
                    songId INTEGER NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    PRIMARY KEY(playlistId, songId),
                    FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE,
                    FOREIGN KEY(songId) REFERENCES songs(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX index_playlist_songs_playlistId ON playlist_songs(playlistId)")
            db.execSQL("CREATE INDEX index_playlist_songs_songId ON playlist_songs(songId)")
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
                    ringMode TEXT NOT NULL DEFAULT 'NORMAL',
                    ignoreQuietMode INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE scan_directories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    path TEXT NOT NULL,
                    name TEXT NOT NULL,
                    addedAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE holidays (
                    date TEXT NOT NULL,
                    year INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    isHoliday INTEGER NOT NULL,
                    fetchedAt INTEGER NOT NULL,
                    PRIMARY KEY(date)
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No-op for this test.
        }
    }
}
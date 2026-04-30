package com.rabbithole.musicbbit.service

import android.content.Intent
import android.os.Looper
import android.content.IntentFilter
import com.rabbithole.musicbbit.data.local.AppDatabase
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistDao
import com.rabbithole.musicbbit.data.local.dao.PlaylistSongDao
import com.rabbithole.musicbbit.data.local.dao.SongDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.data.model.PlaylistSongEntity
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.data.repository.PlaylistRepositoryImpl
import com.rabbithole.musicbbit.di.TestDatabaseModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [AlarmReceiver].
 *
 * Includes:
 *   - Smoke tests verifying intent dispatch to [MusicPlaybackService]
 *   - End-to-end test verifying the full alarm-fire chain: receiver -> service ->
 *     [AlarmFireSession.fire] -> DAO update (one-time alarm disabled)
 *
 * Uses Hilt test injection with an in-memory Room database.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = dagger.hilt.android.testing.HiltTestApplication::class)
@UninstallModules(com.rabbithole.musicbbit.di.DatabaseModule::class)
class AlarmReceiverTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var alarmDao: AlarmDao

    @Inject
    lateinit var songDao: SongDao

    @Inject
    lateinit var playlistDao: PlaylistDao

    @Inject
    lateinit var playlistSongDao: PlaylistSongDao

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `onReceive with valid alarmId starts foreground service`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 1L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val startedService = shadowOf(context).peekNextStartedService()
        assertNotNull("Expected service to be started", startedService)
    }

    @Test
    fun `onReceive with invalid alarmId does not start service`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(context, intent)

        Thread.sleep(100)

        val startedService = shadowOf(context).peekNextStartedService()
        assertNull("Expected no service to be started", startedService)
    }

    @Test
    fun `onReceive with missing alarmId does not crash and does not start service`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, AlarmReceiver::class.java)

        val receiver = AlarmReceiver()
        receiver.onReceive(context, intent)

        Thread.sleep(100)

        val startedService = shadowOf(context).peekNextStartedService()
        assertNull("Expected no service to be started", startedService)
    }

    /**
     * End-to-end test: one-time alarm is triggered, the full chain runs, and the alarm
     * is auto-disabled because it is one-time (repeatDaysBitmask == 0).
     *
     * Chain:
     *   1. Insert one-time alarm + playlist + songs into in-memory DB
     *   2. Trigger AlarmReceiver.onReceive with alarmId
     *   3. Receiver starts MusicPlaybackService
     *   4. Service.onStartCommand calls AlarmFireSession.fire()
     *   5. AlarmFireSession.bookkeepAlarmTrigger() updates DAO: isEnabled=false
     *   6. Verify DAO returns isEnabled=false
     */
    @Test
    fun `one-time alarm triggered via receiver is disabled after fire`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 42L
        val playlistId = 10L
        val songId = 100L

        // --- Seed the in-memory database ---
        val alarm = AlarmEntity(
            id = alarmId,
            hour = 7,
            minute = 30,
            repeatDaysBitmask = 0, // one-time
            excludeHolidays = false,
            playlistId = playlistId,
            isEnabled = true,
            label = "Test One-Time Alarm",
            autoStop = null,
            lastTriggeredAt = null
        )
        alarmDao.insert(alarm)

        val playlist = Playlist(
            id = playlistId,
            name = "Test Playlist",
            createdAt = 0L,
            updatedAt = 0L
        )
        playlistDao.insert(playlist)

        val song = Song(
            id = songId,
            path = "/tmp/test_song.mp3",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 180_000L,
            dateAdded = 0L,
            coverUri = null
        )
        songDao.insert(song)

        val playlistSong = PlaylistSongEntity(
            playlistId = playlistId,
            songId = songId,
            sortOrder = 0
        )
        playlistSongDao.insert(playlistSong)

        // --- Verify alarm is enabled before trigger ---
        val beforeAlarm = alarmDao.getById(alarmId)
        assertNotNull(beforeAlarm)
        assertTrue("Alarm should be enabled before trigger", beforeAlarm!!.isEnabled)

        // --- Verify playlist data is queryable through PlaylistRepositoryImpl ---
        val playlistRepo = PlaylistRepositoryImpl(
            playlistDao, playlistSongDao, songDao, Dispatchers.IO
        )
        val playlistWithSongs = playlistRepo.getPlaylistWithSongs(playlistId).first()
        assertNotNull("PlaylistWithSongs should not be null before trigger", playlistWithSongs)
        assertEquals("Playlist should have 1 song", 1, playlistWithSongs!!.songs.size)
        assertEquals("Song title should match", "Test Song", playlistWithSongs.songs[0].title)

        // --- Trigger the alarm receiver ---
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val receiver = AlarmReceiver()
        receiver.onReceive(context, intent)

        // Wait for the coroutine inside onReceive to dispatch the service intent
        Thread.sleep(500)

        // --- Manually start the service (Robolectric does not auto-start services) ---
        val serviceIntent = shadowOf(context).peekNextStartedService()
        assertNotNull("Service should have been started", serviceIntent)

        val service = Robolectric.setupService(MusicPlaybackService::class.java)
        service.onCreate()
        service.onStartCommand(serviceIntent, 0, 0)

        // AlarmFireSession.fire() uses withContext(mainDispatcher) which posts to the main
        // looper. We must idle it so the coroutine can resume and reach bookkeepAlarmTrigger.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // Poll until AlarmFireSession completes bookkeepAlarmTrigger (IO + main dispatchers)
        var afterAlarm: AlarmEntity? = null
        var retries = 0
        var lastState: String? = null
        while (retries < 30) {
            afterAlarm = alarmDao.getById(alarmId)
            val sessionState = service.alarmFireSession.state.value
            lastState = sessionState.toString()
            if (afterAlarm != null && !afterAlarm.isEnabled) break
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(100)
            retries++
        }

        // --- Verify the alarm is now disabled ---
        assertNotNull("Alarm should still exist in DAO", afterAlarm)
        assertFalse(
            "One-time alarm should be disabled after trigger (retries=$retries, lastState=$lastState)",
            afterAlarm!!.isEnabled
        )
        assertNotNull("lastTriggeredAt should be set", afterAlarm.lastTriggeredAt)

        // Clean up
        service.onDestroy()
    }
}

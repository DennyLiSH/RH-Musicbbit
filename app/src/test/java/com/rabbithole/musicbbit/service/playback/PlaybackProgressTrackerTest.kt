package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.service.PlaybackState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressTrackerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var playerPort: PlayerPort
    private lateinit var repository: PlaybackProgressRepository
    private lateinit var tracker: PlaybackProgressTracker

    private var fakeTimeMs: Long = 1000L
    private var mutableState: PlaybackState = PlaybackState()

    companion object {
        private val TEST_SONG = Song(
            id = 42L,
            path = "/music/test.mp3",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 200_000L,
            dateAdded = 0L,
            coverUri = null,
        )

        @JvmStatic
        @BeforeClass
        fun plantTimber() {
            Timber.uprootAll()
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {}
            })
        }
    }

    @Before
    fun setUp() {
        playerPort = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        fakeTimeMs = 1000L
        mutableState = PlaybackState()

        every { playerPort.currentPositionMs() } returns 5000L
        coEvery { repository.saveProgress(any()) } returns Result.success(Unit)

        tracker = PlaybackProgressTracker(
            scope = testScope,
            playbackProgressRepository = repository,
            playerPort = playerPort,
            getState = { mutableState },
            currentTimeMs = { fakeTimeMs },
        )
    }

    // ---- saveProgress() creates PlaybackProgress with correct values ----

    @Test
    fun `saveProgress creates PlaybackProgress with correct songId, positionMs, playlistId and currentTimeMs`() =
        testScope.runTest {
            mutableState = PlaybackState(
                currentSong = TEST_SONG,
                currentPlaylistId = 99L,
            )
            every { playerPort.currentPositionMs() } returns 12_345L
            fakeTimeMs = 9_999_000L

            tracker.saveProgress()

            val progressSlot = slot<PlaybackProgress>()
            coVerify { repository.saveProgress(capture(progressSlot)) }

            val captured = progressSlot.captured
            assertEquals(42L, captured.songId)
            assertEquals(12_345L, captured.positionMs)
            assertEquals(99L, captured.playlistId)
            assertEquals(9_999_000L, captured.updatedAt)
        }

    // ---- saveProgress() skips when no current song ----

    @Test
    fun `saveProgress skips when currentSong is null`() = testScope.runTest {
        mutableState = PlaybackState(currentSong = null)

        tracker.saveProgress()

        coVerify(exactly = 0) { repository.saveProgress(any()) }
    }

    // ---- saveProgress() calls repository ----

    @Test
    fun `saveProgress calls repository and delivers success`() = testScope.runTest {
        mutableState = PlaybackState(
            currentSong = TEST_SONG,
            currentPlaylistId = 10L,
        )

        tracker.saveProgress()

        val progressSlot = slot<PlaybackProgress>()
        coVerify(exactly = 1) { repository.saveProgress(capture(progressSlot)) }

        assertEquals(TEST_SONG.id, progressSlot.captured.songId)
    }

    // ---- startSaveLoop() periodically calls saveProgress ----

    @Test
    fun `startSaveLoop periodically calls saveProgress at the given interval`() =
        testScope.runTest {
            mutableState = PlaybackState(
                currentSong = TEST_SONG,
                currentPlaylistId = 1L,
            )

            val intervalMs = 5000L
            tracker.startSaveLoop(intervalMs)

            // Initially no saves yet (delay comes first in the loop)
            coVerify(exactly = 0) { repository.saveProgress(any()) }

            advanceTimeBy(intervalMs)
            coVerify(exactly = 1) { repository.saveProgress(any()) }

            advanceTimeBy(intervalMs)
            coVerify(exactly = 2) { repository.saveProgress(any()) }

            advanceTimeBy(intervalMs)
            coVerify(exactly = 3) { repository.saveProgress(any()) }

            tracker.stopSaveLoop()
        }

    // ---- stopSaveLoop() cancels the periodic save ----

    @Test
    fun `stopSaveLoop cancels periodic saves`() = testScope.runTest {
        mutableState = PlaybackState(
            currentSong = TEST_SONG,
            currentPlaylistId = 1L,
        )

        val intervalMs = 5000L
        tracker.startSaveLoop(intervalMs)

        advanceTimeBy(intervalMs)
        coVerify(exactly = 1) { repository.saveProgress(any()) }

        tracker.stopSaveLoop()

        advanceTimeBy(intervalMs * 3)
        // Still only 1 call — loop was cancelled
        coVerify(exactly = 1) { repository.saveProgress(any()) }
    }
}

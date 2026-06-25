package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.service.PlaybackState
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
        playerPort = mock()
        repository = mock()
        fakeTimeMs = 1000L
        mutableState = PlaybackState()

        whenever(playerPort.currentPositionMs()).thenReturn(5000L)
        wheneverBlocking { repository.saveProgress(any()) } doReturn Result.success(Unit)

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
            whenever(playerPort.currentPositionMs()).thenReturn(12_345L)
            fakeTimeMs = 9_999_000L

            tracker.saveProgress()

            val progressCaptor = argumentCaptor<PlaybackProgress>()
            verifyBlocking(repository) { saveProgress(progressCaptor.capture()) }

            val captured = progressCaptor.firstValue
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

        verifyBlocking(repository, org.mockito.Mockito.never()) { saveProgress(any()) }
    }

    // ---- saveProgress() calls repository ----

    @Test
    fun `saveProgress calls repository and delivers success`() = testScope.runTest {
        mutableState = PlaybackState(
            currentSong = TEST_SONG,
            currentPlaylistId = 10L,
        )

        tracker.saveProgress()

        val progressCaptor = argumentCaptor<PlaybackProgress>()
        verifyBlocking(repository) { saveProgress(progressCaptor.capture()) }

        assertEquals(TEST_SONG.id, progressCaptor.firstValue.songId)
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
            verifyBlocking(repository, org.mockito.Mockito.never()) { saveProgress(any()) }

            advanceTimeBy(intervalMs)
            runCurrent()
            verifyBlocking(repository, org.mockito.Mockito.times(1)) { saveProgress(any()) }

            advanceTimeBy(intervalMs)
            runCurrent()
            verifyBlocking(repository, org.mockito.Mockito.times(2)) { saveProgress(any()) }

            advanceTimeBy(intervalMs)
            runCurrent()
            verifyBlocking(repository, org.mockito.Mockito.times(3)) { saveProgress(any()) }

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
        runCurrent()
        verifyBlocking(repository, org.mockito.Mockito.times(1)) { saveProgress(any()) }

        tracker.stopSaveLoop()

        advanceTimeBy(intervalMs * 3)
        runCurrent()
        // Still only 1 call — loop was cancelled
        verifyBlocking(repository, org.mockito.Mockito.times(1)) { saveProgress(any()) }
    }
}

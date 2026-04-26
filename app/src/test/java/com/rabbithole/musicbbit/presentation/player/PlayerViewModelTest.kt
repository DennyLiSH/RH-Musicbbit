package com.rabbithole.musicbbit.presentation.player

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.MusicPlayerStateHolder
import com.rabbithole.musicbbit.service.PlaybackState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [PlayerViewModel] focusing on the dynamic [alarmLabel] flow.
 *
 * - alarmLabel is null when alarmId is null
 * - alarmLabel loaded from repository when alarmId present
 * - alarmLabel cleared when alarmId becomes null
 * - alarmLabel is null when repository returns null
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var stateHolder: MusicPlayerStateHolder
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var playbackStateFlow: MutableStateFlow<PlaybackState>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playbackStateFlow = MutableStateFlow(PlaybackState())
        stateHolder = mockk(relaxed = true) {
            every { playbackState } returns playbackStateFlow
        }
        alarmRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `alarmLabel is null when alarmId is null`() = runTest(testDispatcher) {
        playbackStateFlow.value = PlaybackState(alarmId = null)

        val viewModel = PlayerViewModel(stateHolder, alarmRepository)

        assertNull(viewModel.alarmLabel.value)
    }

    @Test
    fun `alarmLabel loaded from repository when alarmId present`() = runTest(testDispatcher) {
        val alarm = Alarm(
            id = 1L,
            hour = 7,
            minute = 30,
            repeatDays = emptySet(),
            playlistId = 10L,
            isEnabled = true,
            label = "Morning Jog",
            autoStopMinutes = 10,
            lastTriggeredAt = null
        )
        coEvery { alarmRepository.getAlarmById(1L) } returns alarm
        playbackStateFlow.value = PlaybackState(alarmId = 1L)

        val viewModel = PlayerViewModel(stateHolder, alarmRepository)
        advanceUntilIdle()

        assertEquals("Morning Jog", viewModel.alarmLabel.value)
    }

    @Test
    fun `alarmLabel cleared when alarmId becomes null`() = runTest(testDispatcher) {
        val alarm = Alarm(
            id = 1L,
            hour = 7,
            minute = 30,
            repeatDays = emptySet(),
            playlistId = 10L,
            isEnabled = true,
            label = "Morning Jog",
            autoStopMinutes = 10,
            lastTriggeredAt = null
        )
        coEvery { alarmRepository.getAlarmById(1L) } returns alarm
        playbackStateFlow.value = PlaybackState(alarmId = 1L)

        val viewModel = PlayerViewModel(stateHolder, alarmRepository)
        advanceUntilIdle()
        assertEquals("Morning Jog", viewModel.alarmLabel.value)

        playbackStateFlow.value = PlaybackState(alarmId = null)
        advanceUntilIdle()

        assertNull(viewModel.alarmLabel.value)
    }

    @Test
    fun `alarmLabel is null when repository returns null`() = runTest(testDispatcher) {
        coEvery { alarmRepository.getAlarmById(1L) } returns null
        playbackStateFlow.value = PlaybackState(alarmId = 1L)

        val viewModel = PlayerViewModel(stateHolder, alarmRepository)

        assertNull(viewModel.alarmLabel.value)
    }
}

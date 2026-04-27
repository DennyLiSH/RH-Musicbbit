package com.rabbithole.musicbbit.presentation.player

import android.content.Context
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.PlaybackState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var alarmRepository: AlarmRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        alarmRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `alarmLabel is null when alarmId is null`() = runTest(testDispatcher) {
        val viewModel = PlayerViewModel(context, alarmRepository)
        viewModel._playbackState.value = PlaybackState(alarmId = null)
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

        val viewModel = PlayerViewModel(context, alarmRepository)
        viewModel._playbackState.value = PlaybackState(alarmId = 1L)
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

        val viewModel = PlayerViewModel(context, alarmRepository)
        viewModel._playbackState.value = PlaybackState(alarmId = 1L)
        advanceUntilIdle()
        assertEquals("Morning Jog", viewModel.alarmLabel.value)

        viewModel._playbackState.value = PlaybackState(alarmId = null)
        advanceUntilIdle()

        assertNull(viewModel.alarmLabel.value)
    }

    @Test
    fun `alarmLabel is null when repository returns null`() = runTest(testDispatcher) {
        coEvery { alarmRepository.getAlarmById(1L) } returns null

        val viewModel = PlayerViewModel(context, alarmRepository)
        viewModel._playbackState.value = PlaybackState(alarmId = 1L)

        assertNull(viewModel.alarmLabel.value)
    }
}

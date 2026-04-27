package com.rabbithole.musicbbit.presentation.alarm

import android.content.Context
import android.os.Build
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * JVM unit tests for [AlarmListViewModel] focusing on full-screen intent (FSI) permission state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var holidayRepository: HolidayRepository
    private lateinit var playlistRepository: PlaylistRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        alarmRepository = mockk {
            every { getAllAlarms() } returns flowOf(emptyList())
        }
        holidayRepository = mockk(relaxed = true)
        playlistRepository = mockk(relaxed = true)
        mockkObject(FullScreenIntentPermissionHelper)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `fullScreenIntentGranted is true on API below 34`() {
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true

        val viewModel = createViewModel()

        assertTrue(viewModel.isFullScreenIntentGranted.value)
    }

    @Test
    fun `fullScreenIntentGranted reflects helper result granted on API 34+`() {
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true

        val viewModel = createViewModel()

        assertTrue(viewModel.isFullScreenIntentGranted.value)
    }

    @Test
    fun `fullScreenIntentGranted reflects helper result denied on API 34+`() {
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns false

        val viewModel = createViewModel()

        assertFalse(viewModel.isFullScreenIntentGranted.value)
    }

    @Test
    fun `refreshFullScreenIntentStatus updates state`() {
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns false

        val viewModel = createViewModel()
        assertFalse(viewModel.isFullScreenIntentGranted.value)

        // User grants permission in settings
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true
        viewModel.refreshFullScreenIntentStatus()

        assertTrue(viewModel.isFullScreenIntentGranted.value)
    }

    private fun createViewModel(): AlarmListViewModel {
        return AlarmListViewModel(
            alarmRepository = alarmRepository,
            holidayRepository = holidayRepository,
            playlistRepository = playlistRepository,
            context = context
        )
    }
}

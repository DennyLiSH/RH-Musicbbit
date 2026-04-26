package com.rabbithole.musicbbit.presentation.alarm

import android.content.Context
import android.os.Build
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.domain.usecase.DeleteAlarmUseCase
import com.rabbithole.musicbbit.domain.usecase.EnableAlarmUseCase
import com.rabbithole.musicbbit.domain.usecase.GetAlarmsUseCase
import com.rabbithole.musicbbit.domain.usecase.RefreshHolidaysUseCase
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
    private lateinit var getAlarmsUseCase: GetAlarmsUseCase
    private lateinit var deleteAlarmUseCase: DeleteAlarmUseCase
    private lateinit var enableAlarmUseCase: EnableAlarmUseCase
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var refreshHolidaysUseCase: RefreshHolidaysUseCase

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        getAlarmsUseCase = mockk {
            every { this@mockk.invoke() } returns flowOf(emptyList())
        }
        deleteAlarmUseCase = mockk(relaxed = true)
        enableAlarmUseCase = mockk(relaxed = true)
        playlistRepository = mockk(relaxed = true)
        refreshHolidaysUseCase = mockk {
            coEvery { this@mockk.invoke(any()) } returns Result.success(Unit)
        }
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
            getAlarmsUseCase = getAlarmsUseCase,
            deleteAlarmUseCase = deleteAlarmUseCase,
            enableAlarmUseCase = enableAlarmUseCase,
            playlistRepository = playlistRepository,
            refreshHolidaysUseCase = refreshHolidaysUseCase,
            context = context
        )
    }
}

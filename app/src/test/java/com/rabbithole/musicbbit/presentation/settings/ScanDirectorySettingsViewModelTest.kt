package com.rabbithole.musicbbit.presentation.settings

import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import com.rabbithole.musicbbit.domain.usecase.AddScanDirectoryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanDirectorySettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var scanDirectoryRepository: ScanDirectoryRepository
    private lateinit var addScanDirectoryUseCase: AddScanDirectoryUseCase
    private lateinit var alarmRingSettingsRepository: AlarmRingSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scanDirectoryRepository = mockk(relaxed = true)
        addScanDirectoryUseCase = mockk()
        alarmRingSettingsRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load directories emits Success with list`() = runTest(testDispatcher) {
        val directories = listOf(
            ScanDirectory(id = 1L, path = "/music", name = "Music", addedAt = 0L),
            ScanDirectory(id = 2L, path = "/download", name = "Downloads", addedAt = 0L)
        )
        every { scanDirectoryRepository.getAll() } returns flowOf(directories)
        every { alarmRingSettingsRepository.isBreathingEnabled() } returns flowOf(true)
        every { alarmRingSettingsRepository.getBreathingPeriodMs() } returns flowOf(3500L)

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            addScanDirectoryUseCase,
            alarmRingSettingsRepository
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as ScanDirectorySettingsUiState.Success
        assertEquals(2, state.directories.size)
        assertEquals("Music", state.directories[0].name)
    }

    @Test
    fun `add directory success clears pendingDirectory`() = runTest(testDispatcher) {
        every { scanDirectoryRepository.getAll() } returns flowOf(emptyList())
        every { alarmRingSettingsRepository.isBreathingEnabled() } returns flowOf(true)
        every { alarmRingSettingsRepository.getBreathingPeriodMs() } returns flowOf(3500L)
        coEvery { addScanDirectoryUseCase(any()) } returns Result.success(1L)

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            addScanDirectoryUseCase,
            alarmRingSettingsRepository
        )
        advanceUntilIdle()

        viewModel.onAction(ScanDirectorySettingsAction.OnScanDirectoryPreview("/music", "Music"))
        advanceUntilIdle()

        viewModel.onAction(ScanDirectorySettingsAction.OnConfirmAddDirectory)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ScanDirectorySettingsUiState.Success
        assertNull(state.pendingDirectory)
        assertNull(state.errorMessageResId)
    }

    @Test
    fun `add directory failure sets error`() = runTest(testDispatcher) {
        every { scanDirectoryRepository.getAll() } returns flowOf(emptyList())
        every { alarmRingSettingsRepository.isBreathingEnabled() } returns flowOf(true)
        every { alarmRingSettingsRepository.getBreathingPeriodMs() } returns flowOf(3500L)
        coEvery { addScanDirectoryUseCase(any()) } returns Result.failure(RuntimeException("Failed"))

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            addScanDirectoryUseCase,
            alarmRingSettingsRepository
        )
        advanceUntilIdle()

        viewModel.onAction(ScanDirectorySettingsAction.OnScanDirectoryPreview("/music", "Music"))
        advanceUntilIdle()

        viewModel.onAction(ScanDirectorySettingsAction.OnConfirmAddDirectory)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ScanDirectorySettingsUiState.Success
        assertEquals(R.string.settings_error_add_failed, state.errorMessageResId)
        assertNull(state.pendingDirectory)
    }

    @Test
    fun `remove directory calls repository remove`() = runTest(testDispatcher) {
        every { scanDirectoryRepository.getAll() } returns flowOf(emptyList())
        every { alarmRingSettingsRepository.isBreathingEnabled() } returns flowOf(true)
        every { alarmRingSettingsRepository.getBreathingPeriodMs() } returns flowOf(3500L)
        coEvery { scanDirectoryRepository.remove(any()) } returns Result.success(Unit)

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            addScanDirectoryUseCase,
            alarmRingSettingsRepository
        )
        advanceUntilIdle()

        viewModel.onAction(ScanDirectorySettingsAction.OnRemoveDirectory(1L))
        advanceUntilIdle()

        coVerify { scanDirectoryRepository.remove(1L) }
    }
}

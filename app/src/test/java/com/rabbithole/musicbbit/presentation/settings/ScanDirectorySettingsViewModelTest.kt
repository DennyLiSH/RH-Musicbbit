package com.rabbithole.musicbbit.presentation.settings

import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var scanDirectoryRepository: ScanDirectoryRepository
    private lateinit var musicRepository: MusicRepository
    private lateinit var alarmRingSettingsRepository: AlarmRingSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scanDirectoryRepository = mockk(relaxed = true)
        musicRepository = mockk(relaxed = true)
        alarmRingSettingsRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load directories emits Success with list`() = runTest {
        val directories = listOf(
            ScanDirectory(id = 1L, path = "/music", name = "Music", addedAt = 0L),
            ScanDirectory(id = 2L, path = "/download", name = "Downloads", addedAt = 0L)
        )
        every { scanDirectoryRepository.getAll() } returns flowOf(directories)
        every { alarmRingSettingsRepository.isBreathingEnabled() } returns flowOf(true)
        every { alarmRingSettingsRepository.getBreathingPeriodMs() } returns flowOf(3500L)

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            musicRepository,
            alarmRingSettingsRepository
        )

        val state = viewModel.uiState.value as ScanDirectorySettingsUiState.Success
        assertEquals(2, state.directories.size)
        assertEquals("Music", state.directories[0].name)
    }

    @Test
    fun `add directory success clears pendingDirectory`() = runTest {
        every { scanDirectoryRepository.getAll() } returns flowOf(emptyList())
        every { alarmRingSettingsRepository.isBreathingEnabled() } returns flowOf(true)
        every { alarmRingSettingsRepository.getBreathingPeriodMs() } returns flowOf(3500L)
        coEvery { scanDirectoryRepository.add(any()) } coAnswers { Result.success(1L) }
        coEvery { musicRepository.refreshSongs() } returns Result.success(Unit)

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            musicRepository,
            alarmRingSettingsRepository
        )

        val tempDir = System.getProperty("java.io.tmpdir")!!
        viewModel.onAction(ScanDirectorySettingsAction.OnScanDirectoryPreview(tempDir, "Temp"))

        viewModel.onAction(ScanDirectorySettingsAction.OnConfirmAddDirectory)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ScanDirectorySettingsUiState.Success
        assertNull(state.pendingDirectory)
        assertNull(state.errorMessageResId)
    }

    @Test
    fun `add directory failure sets error`() = runTest(testDispatcher) {
        every { scanDirectoryRepository.getAll() } returns flowOf(emptyList())
        every { alarmRingSettingsRepository.isBreathingEnabled() } returns flowOf(true)
        every { alarmRingSettingsRepository.getBreathingPeriodMs() } returns flowOf(3500L)
        coEvery { scanDirectoryRepository.add(any()) } returns Result.failure(RuntimeException("Failed"))

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            musicRepository,
            alarmRingSettingsRepository
        )

        val tempDir = System.getProperty("java.io.tmpdir")!!
        viewModel.onAction(ScanDirectorySettingsAction.OnScanDirectoryPreview(tempDir, "Temp"))

        viewModel.onAction(ScanDirectorySettingsAction.OnConfirmAddDirectory)

        val state = viewModel.uiState.value as ScanDirectorySettingsUiState.Success
        assertEquals(R.string.settings_error_add_failed, state.errorMessageResId)
        assertNull(state.pendingDirectory)
    }

    @Test
    fun `remove directory calls repository remove`() = runTest {
        every { scanDirectoryRepository.getAll() } returns flowOf(emptyList())
        every { alarmRingSettingsRepository.isBreathingEnabled() } returns flowOf(true)
        every { alarmRingSettingsRepository.getBreathingPeriodMs() } returns flowOf(3500L)
        coEvery { scanDirectoryRepository.remove(any()) } returns Result.success(Unit)

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository,
            musicRepository,
            alarmRingSettingsRepository
        )

        viewModel.onAction(ScanDirectorySettingsAction.OnRemoveDirectory(1L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { scanDirectoryRepository.remove(1L) }
    }

    @Test
    fun `retry reloads directories after error`() = runTest(testDispatcher) {
        val errorFlow = kotlinx.coroutines.flow.flow<List<ScanDirectory>> { throw RuntimeException("DB error") }
        every { scanDirectoryRepository.getAll() } returns errorFlow
        every { alarmRingSettingsRepository.isBreathingEnabled() } returns flowOf(true)
        every { alarmRingSettingsRepository.getBreathingPeriodMs() } returns flowOf(3500L)

        val viewModel = ScanDirectorySettingsViewModel(
            scanDirectoryRepository, musicRepository, alarmRingSettingsRepository
        )

        assertTrue(viewModel.uiState.value is ScanDirectorySettingsUiState.Error)

        every { scanDirectoryRepository.getAll() } returns flowOf(
            listOf(ScanDirectory(id = 1L, path = "/music", name = "Music", addedAt = 0L))
        )
        viewModel.retry()

        val state = viewModel.uiState.value as ScanDirectorySettingsUiState.Success
        assertEquals(1, state.directories.size)
    }
}

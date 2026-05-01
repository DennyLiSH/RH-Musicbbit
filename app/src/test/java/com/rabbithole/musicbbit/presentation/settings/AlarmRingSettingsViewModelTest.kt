package com.rabbithole.musicbbit.presentation.settings

import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmRingSettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var alarmRingSettingsRepository: AlarmRingSettingsRepository

    companion object {
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
        Dispatchers.setMain(testDispatcher)
        alarmRingSettingsRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads volume ramp duration from repository`() = runTest {
        every { alarmRingSettingsRepository.getVolumeRampDurationSeconds() } returns flowOf(10)

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)

        assertEquals(10, viewModel.uiState.value.volumeRampDurationSeconds)
    }

    @Test
    fun `setVolumeRampDuration forwards to repository`() = runTest {
        every { alarmRingSettingsRepository.getVolumeRampDurationSeconds() } returns flowOf(5)
        coEvery { alarmRingSettingsRepository.setVolumeRampDurationSeconds(15) } returns Result.success(Unit)

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)

        viewModel.setVolumeRampDuration(15)

        coVerify { alarmRingSettingsRepository.setVolumeRampDurationSeconds(15) }
    }

    @Test
    fun `uiState updates when repository emits new value`() = runTest {
        val volumeRampFlow = MutableStateFlow(5)
        every { alarmRingSettingsRepository.getVolumeRampDurationSeconds() } returns volumeRampFlow

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)
        assertEquals(5, viewModel.uiState.value.volumeRampDurationSeconds)

        volumeRampFlow.value = 20
        assertEquals(20, viewModel.uiState.value.volumeRampDurationSeconds)
    }

    @Test
    fun `repository error does not crash uiState`() = runTest {
        every { alarmRingSettingsRepository.getVolumeRampDurationSeconds() } returns flowOf(5)
        coEvery { alarmRingSettingsRepository.setVolumeRampDurationSeconds(10) } returns Result.failure(RuntimeException("Failed"))

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)
        assertEquals(5, viewModel.uiState.value.volumeRampDurationSeconds)

        viewModel.setVolumeRampDuration(10)

        assertEquals(5, viewModel.uiState.value.volumeRampDurationSeconds)
    }
}

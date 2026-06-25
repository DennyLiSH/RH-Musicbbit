package com.rabbithole.musicbbit.presentation.settings

import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.mockito.Mockito.times
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        alarmRingSettingsRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads volume ramp duration from repository`() = runTest {
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(flowOf(10))
        whenever(alarmRingSettingsRepository.isBreathingEnabled()).thenReturn(flowOf(false))
        whenever(alarmRingSettingsRepository.getBreathingPeriodMs()).thenReturn(flowOf(3000L))

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)

        assertEquals(10, viewModel.uiState.value.volumeRampDurationSeconds)
    }

    @Test
    fun `setVolumeRampDuration forwards to repository`() = runTest {
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(flowOf(5))
        whenever(alarmRingSettingsRepository.isBreathingEnabled()).thenReturn(flowOf(false))
        whenever(alarmRingSettingsRepository.getBreathingPeriodMs()).thenReturn(flowOf(3000L))
        wheneverBlocking { alarmRingSettingsRepository.setVolumeRampDurationSeconds(15) } doReturn Result.success(Unit)

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)

        viewModel.setVolumeRampDuration(15)

        verifyBlocking(alarmRingSettingsRepository) { setVolumeRampDurationSeconds(15) }
    }

    @Test
    fun `uiState updates when volume ramp flow emits new value`() = runTest {
        val volumeRampFlow = MutableStateFlow(5)
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(volumeRampFlow)
        whenever(alarmRingSettingsRepository.isBreathingEnabled()).thenReturn(flowOf(false))
        whenever(alarmRingSettingsRepository.getBreathingPeriodMs()).thenReturn(flowOf(3000L))

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)
        assertEquals(5, viewModel.uiState.value.volumeRampDurationSeconds)

        volumeRampFlow.value = 20
        assertEquals(20, viewModel.uiState.value.volumeRampDurationSeconds)
    }

    @Test
    fun `setVolumeRampDuration failure leaves uiState unchanged`() = runTest {
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(flowOf(5))
        whenever(alarmRingSettingsRepository.isBreathingEnabled()).thenReturn(flowOf(false))
        whenever(alarmRingSettingsRepository.getBreathingPeriodMs()).thenReturn(flowOf(3000L))
        wheneverBlocking { alarmRingSettingsRepository.setVolumeRampDurationSeconds(10) } doReturn Result.failure(RuntimeException("Failed"))

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)
        assertEquals(5, viewModel.uiState.value.volumeRampDurationSeconds)

        viewModel.setVolumeRampDuration(10)

        assertEquals(5, viewModel.uiState.value.volumeRampDurationSeconds)
    }

    @Test
    fun `init loads breathing settings from repository`() = runTest {
        val breathingEnabledFlow = MutableStateFlow(true)
        val breathingPeriodFlow = MutableStateFlow(4000L)
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(flowOf(5))
        whenever(alarmRingSettingsRepository.isBreathingEnabled()).thenReturn(breathingEnabledFlow)
        whenever(alarmRingSettingsRepository.getBreathingPeriodMs()).thenReturn(breathingPeriodFlow)

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)

        val state = viewModel.uiState.value
        assertEquals(true, state.breathingEnabled)
        assertEquals(4000L, state.breathingPeriodMs)
    }

    @Test
    fun `setBreathingEnabled forwards to repository`() = runTest {
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(flowOf(5))
        whenever(alarmRingSettingsRepository.isBreathingEnabled()).thenReturn(flowOf(false))
        whenever(alarmRingSettingsRepository.getBreathingPeriodMs()).thenReturn(flowOf(3000L))
        wheneverBlocking { alarmRingSettingsRepository.setBreathingEnabled(any()) } doReturn Result.success(Unit)
        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)

        viewModel.setBreathingEnabled(false)

        verifyBlocking(alarmRingSettingsRepository) { setBreathingEnabled(false) }
    }

    @Test
    fun `setBreathingPeriodMs forwards to repository`() = runTest {
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(flowOf(5))
        whenever(alarmRingSettingsRepository.isBreathingEnabled()).thenReturn(flowOf(false))
        whenever(alarmRingSettingsRepository.getBreathingPeriodMs()).thenReturn(flowOf(3000L))
        wheneverBlocking { alarmRingSettingsRepository.setBreathingPeriodMs(any()) } doReturn Result.success(Unit)
        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)

        viewModel.setBreathingPeriodMs(5000L)

        verifyBlocking(alarmRingSettingsRepository) { setBreathingPeriodMs(5000L) }
    }

    @Test
    fun `setBreathingEnabled failure leaves uiState unchanged`() = runTest {
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(flowOf(5))
        whenever(alarmRingSettingsRepository.isBreathingEnabled()).thenReturn(flowOf(false))
        whenever(alarmRingSettingsRepository.getBreathingPeriodMs()).thenReturn(flowOf(3000L))
        wheneverBlocking { alarmRingSettingsRepository.setBreathingEnabled(any()) } doReturn Result.failure(RuntimeException("boom"))

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)
        assertEquals(false, viewModel.uiState.value.breathingEnabled)

        viewModel.setBreathingEnabled(true)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.breathingEnabled)
        verifyBlocking(alarmRingSettingsRepository, times(1)) { setBreathingEnabled(true) }
    }

    @Test
    fun `setBreathingPeriodMs failure leaves uiState unchanged`() = runTest {
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(flowOf(5))
        whenever(alarmRingSettingsRepository.isBreathingEnabled()).thenReturn(flowOf(false))
        whenever(alarmRingSettingsRepository.getBreathingPeriodMs()).thenReturn(flowOf(3000L))
        wheneverBlocking { alarmRingSettingsRepository.setBreathingPeriodMs(any()) } doReturn Result.failure(RuntimeException("boom"))

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)
        assertEquals(3000L, viewModel.uiState.value.breathingPeriodMs)

        viewModel.setBreathingPeriodMs(5000L)
        advanceUntilIdle()

        assertEquals(3000L, viewModel.uiState.value.breathingPeriodMs)
        verifyBlocking(alarmRingSettingsRepository, times(1)) { setBreathingPeriodMs(5000L) }
    }

    @Test
    fun `breathingEnabled flow emits new value updates uiState`() = runTest {
        val breathingEnabledFlow = MutableStateFlow(true)
        val breathingPeriodFlow = MutableStateFlow(4000L)
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(flowOf(5))
        whenever(alarmRingSettingsRepository.isBreathingEnabled()).thenReturn(breathingEnabledFlow)
        whenever(alarmRingSettingsRepository.getBreathingPeriodMs()).thenReturn(breathingPeriodFlow)

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)
        assertEquals(true, viewModel.uiState.value.breathingEnabled)

        breathingEnabledFlow.value = false
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.breathingEnabled)

        breathingEnabledFlow.value = true
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.breathingEnabled)
    }

    @Test
    fun `breathingPeriodMs flow emits new value updates uiState`() = runTest {
        val breathingEnabledFlow = MutableStateFlow(false)
        val breathingPeriodFlow = MutableStateFlow(3000L)
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(flowOf(5))
        whenever(alarmRingSettingsRepository.isBreathingEnabled()).thenReturn(breathingEnabledFlow)
        whenever(alarmRingSettingsRepository.getBreathingPeriodMs()).thenReturn(breathingPeriodFlow)

        val viewModel = AlarmRingSettingsViewModel(alarmRingSettingsRepository)
        assertEquals(3000L, viewModel.uiState.value.breathingPeriodMs)

        breathingPeriodFlow.value = 6000L
        advanceUntilIdle()
        assertEquals(6000L, viewModel.uiState.value.breathingPeriodMs)

        breathingPeriodFlow.value = 1500L
        advanceUntilIdle()
        assertEquals(1500L, viewModel.uiState.value.breathingPeriodMs)
    }
}

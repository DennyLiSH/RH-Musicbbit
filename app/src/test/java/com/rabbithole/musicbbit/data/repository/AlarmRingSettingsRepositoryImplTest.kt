package com.rabbithole.musicbbit.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmRingSettingsRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testDataStore = PreferenceDataStoreFactory.createForTesting(scope = testScope)
    private lateinit var repository: AlarmRingSettingsRepositoryImpl

    @Before
    fun setup() {
        repository = AlarmRingSettingsRepositoryImpl(testDataStore, testDispatcher)
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `isBreathingEnabled defaults to true`() = runTest(testDispatcher) {
        repository.isBreathingEnabled().test {
            val enabled = awaitItem()
            assertEquals(true, enabled)
            awaitComplete()
        }
    }

    @Test
    fun `getBreathingPeriodMs defaults to 3500L`() = runTest(testDispatcher) {
        repository.getBreathingPeriodMs().test {
            val period = awaitItem()
            assertEquals(3500L, period)
            awaitComplete()
        }
    }

    @Test
    fun `setBreathingEnabled persists and emits new value`() = runTest(testDispatcher) {
        repository.setBreathingEnabled(false)

        repository.isBreathingEnabled().test {
            assertEquals(false, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `setBreathingPeriodMs persists and emits new value`() = runTest(testDispatcher) {
        repository.setBreathingPeriodMs(5000L)

        repository.getBreathingPeriodMs().test {
            assertEquals(5000L, awaitItem())
            awaitComplete()
        }
    }
}

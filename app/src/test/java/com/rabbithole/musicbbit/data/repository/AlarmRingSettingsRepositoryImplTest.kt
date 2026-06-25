package com.rabbithole.musicbbit.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmRingSettingsRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val breathingEnabledKey = booleanPreferencesKey("breathing_enabled")
    private val breathingPeriodMsKey = longPreferencesKey("breathing_period_ms")

    private val prefsMap = mutableMapOf<String, Any>(
        "breathing_enabled" to true,
        "breathing_period_ms" to 3500L,
    )

    private val prefsFlow = MutableStateFlow(createMockPrefs())

    private fun createMockPrefs(): Preferences {
        val prefs = mock<Preferences>()
        whenever(prefs[breathingEnabledKey]).thenAnswer { prefsMap["breathing_enabled"] as? Boolean ?: true }
        whenever(prefs[breathingPeriodMsKey]).thenAnswer { prefsMap["breathing_period_ms"] as? Long ?: 3500L }
        return prefs
    }

    private val dataStore: DataStore<Preferences> = mock<DataStore<Preferences>>().also {
        whenever(it.data).thenReturn(prefsFlow)
    }

    private lateinit var repository: AlarmRingSettingsRepositoryImpl

    @Before
    fun setup() {
        repository = AlarmRingSettingsRepositoryImpl(dataStore, testDispatcher)
    }

    @Test
    fun `isBreathingEnabled defaults to true`() = runTest(testDispatcher) {
        repository.isBreathingEnabled().test {
            val enabled = awaitItem()
            assertEquals(true, enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getBreathingPeriodMs defaults to 3500L`() = runTest(testDispatcher) {
        repository.getBreathingPeriodMs().test {
            val period = awaitItem()
            assertEquals(3500L, period)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isBreathingEnabled reflects updated value`() = runTest(testDispatcher) {
        // Simulate preference change
        prefsMap["breathing_enabled"] = false
        prefsFlow.value = createMockPrefs()

        repository.isBreathingEnabled().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getBreathingPeriodMs reflects updated value`() = runTest(testDispatcher) {
        prefsMap["breathing_period_ms"] = 5000L
        prefsFlow.value = createMockPrefs()

        repository.getBreathingPeriodMs().test {
            assertEquals(5000L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

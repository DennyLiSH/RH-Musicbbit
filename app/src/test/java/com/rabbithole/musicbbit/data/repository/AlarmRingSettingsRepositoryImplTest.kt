package com.rabbithole.musicbbit.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.datastore.SettingsKeys
import com.rabbithole.musicbbit.data.local.datastore.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmRingSettingsRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val holder = MutablePreferencesHolder()
    private val settingsStore = SettingsStore(FakeDataStore(holder), testDispatcher)

    private lateinit var repository: AlarmRingSettingsRepositoryImpl

    @Before
    fun setup() {
        repository = AlarmRingSettingsRepositoryImpl(settingsStore)
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
        holder.set(mutablePreferencesOf(SettingsKeys.BREATHING_ENABLED to false))

        repository.isBreathingEnabled().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getBreathingPeriodMs reflects updated value`() = runTest(testDispatcher) {
        holder.set(mutablePreferencesOf(SettingsKeys.BREATHING_PERIOD_MS to 5000L))

        repository.getBreathingPeriodMs().test {
            assertEquals(5000L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class MutablePreferencesHolder {
        private val flow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val data: kotlinx.coroutines.flow.StateFlow<Preferences> = flow
        fun set(p: Preferences) { flow.value = p }
    }

    private class FakeDataStore(private val holder: MutablePreferencesHolder) :
        androidx.datastore.core.DataStore<Preferences> {
        override val data = holder.data
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(holder.data.value)
            holder.set(next)
            return next
        }
    }
}
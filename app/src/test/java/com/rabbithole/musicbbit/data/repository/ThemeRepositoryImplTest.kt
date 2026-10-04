package com.rabbithole.musicbbit.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.datastore.SettingsKeys
import com.rabbithole.musicbbit.data.local.datastore.SettingsStore
import com.rabbithole.musicbbit.domain.model.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val holder = MutablePreferencesHolder()
    private val settingsStore = SettingsStore(FakeDataStore(holder), testDispatcher)

    private lateinit var repository: ThemeRepositoryImpl

    @Before
    fun setup() {
        repository = ThemeRepositoryImpl(settingsStore)
    }

    @Test
    fun `getThemeMode defaults to SYSTEM`() = runTest(testDispatcher) {
        repository.getThemeMode().test {
            val mode = awaitItem()
            assertEquals(ThemeMode.SYSTEM, mode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getThemeMode reflects DARK value`() = runTest(testDispatcher) {
        holder.set(mutablePreferencesOf(SettingsKeys.THEME_MODE to "DARK"))
        repository.getThemeMode().test {
            val mode = awaitItem()
            assertEquals(ThemeMode.DARK, mode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getThemeMode reflects LIGHT value`() = runTest(testDispatcher) {
        holder.set(mutablePreferencesOf(SettingsKeys.THEME_MODE to "LIGHT"))
        repository.getThemeMode().test {
            assertEquals(ThemeMode.LIGHT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Live preferences backing store: set() updates the state flow so any subscribed
     * collector sees the new value. Tests can reseed `initial` before re-subscribing.
     */
    private class MutablePreferencesHolder {
        private val flow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val data: kotlinx.coroutines.flow.StateFlow<Preferences> = flow
        fun set(p: Preferences) {
            flow.value = p
        }
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
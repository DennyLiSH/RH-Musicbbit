package com.rabbithole.musicbbit.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.rabbithole.musicbbit.data.local.datastore.SettingsKeys
import com.rabbithole.musicbbit.domain.model.ThemeMode
import io.mockk.every
import io.mockk.mockk
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

    private val themeModeKey = stringPreferencesKey("theme_mode")

    private val prefsMap = mutableMapOf<String, Any?>(
        "theme_mode" to null,
    )

    private val prefsFlow = MutableStateFlow(createMockPrefs())

    private fun createMockPrefs(): Preferences {
        val prefs = mockk<Preferences>(relaxed = true)
        every { prefs[themeModeKey] } answers { prefsMap["theme_mode"] as? String }
        every { prefs.get(themeModeKey) } answers { prefsMap["theme_mode"] as? String }
        return prefs
    }

    private val dataStore: DataStore<Preferences> = mockk(relaxed = true) {
        every { data } returns prefsFlow
    }

    private lateinit var repository: ThemeRepositoryImpl

    @Before
    fun setup() {
        repository = ThemeRepositoryImpl(dataStore, testDispatcher)
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
        prefsMap["theme_mode"] = "DARK"
        prefsFlow.value = createMockPrefs()

        repository.getThemeMode().test {
            val mode = awaitItem()
            assertEquals(ThemeMode.DARK, mode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getThemeMode reflects LIGHT value`() = runTest(testDispatcher) {
        prefsMap["theme_mode"] = "LIGHT"
        prefsFlow.value = createMockPrefs()

        repository.getThemeMode().test {
            assertEquals(ThemeMode.LIGHT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

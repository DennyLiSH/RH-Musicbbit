package com.rabbithole.musicbbit.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.rabbithole.musicbbit.domain.model.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testDataStore = PreferenceDataStoreFactory.createForTesting(scope = testScope)
    private lateinit var repository: ThemeRepositoryImpl

    @Before
    fun setup() {
        repository = ThemeRepositoryImpl(testDataStore, testDispatcher)
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `getThemeMode defaults to SYSTEM`() = runTest(testDispatcher) {
        repository.getThemeMode().test {
            val mode = awaitItem()
            assertEquals(ThemeMode.SYSTEM, mode)
            awaitComplete()
        }
    }

    @Test
    fun `setThemeMode persists and emits new value`() = runTest(testDispatcher) {
        val result = repository.setThemeMode(ThemeMode.DARK)
        assert(result.isSuccess)

        repository.getThemeMode().test {
            val mode = awaitItem()
            assertEquals(ThemeMode.DARK, mode)
            awaitComplete()
        }
    }

    @Test
    fun `getThemeMode emits updated value after set`() = runTest(testDispatcher) {
        // Start collecting first
        repository.getThemeMode().test {
            // Default value
            assertEquals(ThemeMode.SYSTEM, awaitItem())

            // Change to LIGHT
            repository.setThemeMode(ThemeMode.LIGHT)

            // Should emit updated value
            assertEquals(ThemeMode.LIGHT, awaitItem())
        }
    }
}

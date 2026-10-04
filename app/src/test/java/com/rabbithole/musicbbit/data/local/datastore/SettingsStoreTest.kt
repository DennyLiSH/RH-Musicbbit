package com.rabbithole.musicbbit.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

    /**
     * Hand-rolled fake that mirrors DataStore's `data`/`updateData` semantics. Avoids
     * Mockito's struggle to stub suspend functions cleanly while keeping the test JVM-only.
     */
    private class FakeDataStore(initial: Preferences) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    @Test
    fun `string flow returns default when key absent, written value after write`() = runTest {
        val store = SettingsStore(
            FakeDataStore(mutablePreferencesOf()),
            kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val key = stringPreferencesKey("k")
        assertEquals("fallback", store.stringFlow(key, default = "fallback").first())
        assertTrue(store.write(key, "v").isSuccess)
        assertEquals("v", store.stringFlow(key, default = "fallback").first())
    }
}
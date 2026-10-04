package com.rabbithole.musicbbit.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.rabbithole.musicbbit.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Small deep module over the preferences DataStore: keys and their defaults are
 * declared together at call sites; the read/write recipes (map-with-default on IO,
 * Result-wrapped edit) live here once. Every preference consumer goes through this —
 * no direct DataStore handle outside the datastore package.
 */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    fun booleanFlow(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
        dataStore.data.map { it[key] ?: default }.flowOn(ioDispatcher)

    fun intFlow(key: Preferences.Key<Int>, default: Int): Flow<Int> =
        dataStore.data.map { it[key] ?: default }.flowOn(ioDispatcher)

    fun longFlow(key: Preferences.Key<Long>, default: Long): Flow<Long> =
        dataStore.data.map { it[key] ?: default }.flowOn(ioDispatcher)

    fun stringFlow(key: Preferences.Key<String>, default: String): Flow<String> =
        dataStore.data.map { it[key] ?: default }.flowOn(ioDispatcher)

    suspend fun write(key: Preferences.Key<Boolean>, value: Boolean): Result<Unit> =
        writeInternal { it[key] = value }

    suspend fun write(key: Preferences.Key<Int>, value: Int): Result<Unit> =
        writeInternal { it[key] = value }

    suspend fun write(key: Preferences.Key<Long>, value: Long): Result<Unit> =
        writeInternal { it[key] = value }

    suspend fun write(key: Preferences.Key<String>, value: String): Result<Unit> =
        writeInternal { it[key] = value }

    private suspend fun writeInternal(edit: (MutablePreferences) -> Unit): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit(edit)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
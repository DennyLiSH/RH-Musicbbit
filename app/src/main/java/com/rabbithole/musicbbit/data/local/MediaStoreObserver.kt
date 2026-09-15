package com.rabbithole.musicbbit.data.local

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.rabbithole.musicbbit.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Observes MediaStore audio changes and keeps the Room library in sync.
 *
 * Bursts of [onChange] events (mass sync, batch deletes) are conflated into a
 * single [LibraryRefresher.refreshAll] after [REFRESH_DEBOUNCE_MS] of quiet,
 * instead of firing one full scan per event.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Singleton
class MediaStoreObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRefresher: LibraryRefresher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val refreshRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        register()
        scope.launch {
            refreshRequests
                .debounce(REFRESH_DEBOUNCE_MS)
                .collect {
                    libraryRefresher.refreshAll()
                        .onSuccess { r ->
                            Timber.i(
                                "MediaStore change refresh done: inserted=${r.inserted}, " +
                                    "deleted=${r.deleted}, updated=${r.updated}"
                            )
                        }
                        .onFailure { Timber.e(it, "MediaStore change refresh failed") }
                }
        }
    }

    private fun register() {
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        Timber.i("MediaStore changed: uri=$uri")
        refreshRequests.tryEmit(Unit)
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 2_000L
    }
}

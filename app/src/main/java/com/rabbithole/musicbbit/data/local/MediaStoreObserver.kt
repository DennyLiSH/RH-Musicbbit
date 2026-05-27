package com.rabbithole.musicbbit.data.local

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class MediaStoreObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        register()
    }

    private fun register() {
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Timber.i("MediaStore changed, triggering song refresh: uri=$uri")
        scope.launch {
            musicRepository.refreshSongs()
        }
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }
}

package com.rabbithole.musicbbit.service

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class AlarmVolumeController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var originalVolume: Int = -1
    private var volumeJob: Job? = null

    private val maxVolume: Int
        get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun startVolumeRamp(coroutineScope: CoroutineScope) {
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val startVolume = (maxVolume * 0.1f).toInt().coerceAtLeast(1)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, startVolume, 0)
        Timber.d("Starting volume ramp from $startVolume to $maxVolume")

        val steps = 30
        val delayMs = 1000L
        val volumeStep = (maxVolume - startVolume).toFloat() / steps

        volumeJob = coroutineScope.launch {
            repeat(steps) { i ->
                delay(delayMs)
                val newVolume = (startVolume + volumeStep * (i + 1)).toInt()
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    newVolume.coerceIn(0, maxVolume),
                    0
                )
                Timber.d("Volume step ${i + 1}/$steps: $newVolume")
            }
            Timber.i("Volume ramp completed")
        }
    }

    fun restoreVolume() {
        volumeJob?.cancel()
        if (originalVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            Timber.i("Volume restored to $originalVolume")
            originalVolume = -1
        }
    }
}

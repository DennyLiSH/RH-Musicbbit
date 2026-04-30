package com.rabbithole.musicbbit.service

import android.content.Context
import android.media.AudioManager
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import com.rabbithole.musicbbit.service.alarm.ports.VolumeRampPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class AlarmVolumeController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmRingSettingsRepository: AlarmRingSettingsRepository
) : VolumeRampPort {
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var originalVolume: Int = -1
    private var volumeJob: Job? = null

    private var userAdjustedVolume: Int = -1
    private var userAdjusted: Boolean = false

    private val maxVolume: Int
        get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    override fun startVolumeRamp(scope: CoroutineScope) {
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        volumeJob = scope.launch {
            val durationSeconds = alarmRingSettingsRepository.getVolumeRampDurationSeconds().first()

            if (durationSeconds == 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                Timber.i("Volume ramp disabled, set to max immediately")
                return@launch
            }

            val startVolume = (maxVolume * 0.3f).toInt().coerceAtLeast(1)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, startVolume, 0)
            Timber.d("Starting volume ramp from $startVolume to $maxVolume over ${durationSeconds}s")

            val steps = 10
            val delayMs = durationSeconds * 1000L / steps
            val volumeStep = (maxVolume - startVolume).toFloat() / steps

            repeat(steps) { i ->
                delay(delayMs)

                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val expectedVolume = (startVolume + volumeStep * i).toInt().coerceIn(0, maxVolume)

                if (currentVolume != expectedVolume) {
                    userAdjustedVolume = currentVolume
                    userAdjusted = true
                    Timber.i("User manually adjusted volume to $currentVolume, stopping ramp")
                    return@launch
                }

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

    override fun restoreVolume() {
        volumeJob?.cancel()

        val targetVolume = when {
            userAdjusted -> {
                Timber.i("Restoring to user-adjusted volume: $userAdjustedVolume")
                userAdjustedVolume
            }
            originalVolume >= 0 -> {
                Timber.i("Restoring to original volume: $originalVolume")
                originalVolume
            }
            else -> return
        }

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)

        // Reset all state
        originalVolume = -1
        userAdjustedVolume = -1
        userAdjusted = false
    }
}

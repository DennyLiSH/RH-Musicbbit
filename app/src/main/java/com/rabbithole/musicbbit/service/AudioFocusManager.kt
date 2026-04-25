package com.rabbithole.musicbbit.service

import android.annotation.TargetApi
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import timber.log.Timber

/**
 * Manages audio focus for music playback.
 *
 * Requests audio focus when playback starts and abandons it when playback stops.
 * Handles focus change callbacks to pause/resume playback appropriately.
 */
class AudioFocusManager(
    private val context: Context,
    private val onFocusLoss: () -> Unit,
    private val onFocusLossTransient: () -> Unit,
    private val onFocusGain: () -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    /**
     * Request audio focus for music playback.
     *
     * @return true if focus was granted, false otherwise.
     */
    @Suppress("DEPRECATION")
    fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = createFocusRequest()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            val listener = createFocusChangeListener()
            audioFocusChangeListener = listener
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Timber.i("Audio focus requested: granted=$granted")
        return granted
    }

    /**
     * Abandon audio focus.
     */
    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                Timber.i("Audio focus abandoned (API 26+)")
            }
        } else {
            @Suppress("DEPRECATION")
            audioFocusChangeListener?.let {
                audioManager.abandonAudioFocus(it)
                Timber.i("Audio focus abandoned (legacy)")
            }
        }
        audioFocusRequest = null
        audioFocusChangeListener = null
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createFocusRequest(): AudioFocusRequest {
        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                handleFocusChange(focusChange)
            }
            .build()
    }

    private fun createFocusChangeListener(): AudioManager.OnAudioFocusChangeListener {
        return AudioManager.OnAudioFocusChangeListener { focusChange ->
            handleFocusChange(focusChange)
        }
    }

    private fun handleFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.i("Audio focus lost permanently")
                onFocusLoss()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Timber.i("Audio focus lost transiently")
                onFocusLossTransient()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Timber.i("Audio focus lost transiently (can duck)")
                onFocusLossTransient()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.i("Audio focus gained")
                onFocusGain()
            }
        }
    }
}

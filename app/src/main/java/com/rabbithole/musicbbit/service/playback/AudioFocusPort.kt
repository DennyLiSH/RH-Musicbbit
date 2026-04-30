package com.rabbithole.musicbbit.service.playback

interface AudioFocusPort {
    fun requestFocus(): Boolean
    fun abandonFocus()
    fun registerCallbacks(
        onFocusLoss: () -> Unit,
        onFocusLossTransient: () -> Unit,
        onFocusGain: () -> Unit,
    )
}

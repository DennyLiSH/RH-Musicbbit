package com.rabbithole.musicbbit.service.playback

class FakeAudioFocusPort : AudioFocusPort {

    private var focusRequestResult: Boolean = true
    var requestFocusCallCount: Int = 0
        private set
    var abandonFocusCallCount: Int = 0
        private set

    private var focusLossCallback: (() -> Unit)? = null
    private var focusLossTransientCallback: (() -> Unit)? = null
    private var focusGainCallback: (() -> Unit)? = null

    fun setRequestFocusResult(result: Boolean) {
        focusRequestResult = result
    }

    override fun requestFocus(): Boolean {
        requestFocusCallCount++
        return focusRequestResult
    }

    override fun abandonFocus() {
        abandonFocusCallCount++
    }

    override fun registerCallbacks(
        onFocusLoss: () -> Unit,
        onFocusLossTransient: () -> Unit,
        onFocusGain: () -> Unit,
    ) {
        focusLossCallback = onFocusLoss
        focusLossTransientCallback = onFocusLossTransient
        focusGainCallback = onFocusGain
    }

    fun simulateFocusLoss() {
        focusLossCallback?.invoke()
    }

    fun simulateFocusLossTransient() {
        focusLossTransientCallback?.invoke()
    }

    fun simulateFocusGain() {
        focusGainCallback?.invoke()
    }
}

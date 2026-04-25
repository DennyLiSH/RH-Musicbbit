package com.rabbithole.musicbbit.presentation.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.service.AlarmActionReceiver
import com.rabbithole.musicbbit.service.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Full-screen alarm ring activity that appears when an alarm triggers.
 *
 * Shows over the lock screen with large buttons for:
 * - Pause / Resume playback
 * - Stop the alarm completely
 * - Snooze (delay by 5 minutes)
 */
@AndroidEntryPoint
class AlarmRingActivity : ComponentActivity() {

    private val viewModel: AlarmRingViewModel by viewModels()
    private var breathingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) {
            Timber.e("AlarmRingActivity launched without alarmId")
            finish()
            return
        }

        Timber.i("AlarmRingActivity created for alarmId=$alarmId")
        viewModel.loadAlarmLabel(alarmId)

        // Show over lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContent {
            MaterialTheme {
                AlarmRingScreen(
                    alarmId = alarmId,
                    viewModel = viewModel,
                    onStop = { finish() }
                )
            }
        }
    }

    /**
     * Start a breathing light effect that cycles screen brightness
     * between 0.1 and 1.0 with the given period in milliseconds.
     */
    internal fun startBreathingAnimation(periodMs: Long) {
        breathingJob?.cancel()
        breathingJob = lifecycleScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() % periodMs
                val phase = elapsed / periodMs.toDouble()
                val brightness = (0.55f + 0.45f * kotlin.math.sin(phase * 2 * Math.PI)).toFloat()
                window.attributes = window.attributes.apply {
                    screenBrightness = brightness.coerceIn(0.1f, 1.0f)
                }
                delay(BREATHING_UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop the breathing animation and restore normal brightness.
     */
    internal fun stopBreathingAnimation() {
        breathingJob?.cancel()
        breathingJob = null
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        Timber.d("Breathing animation stopped, brightness restored")
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        stopBreathingAnimation()
    }

    override fun onDestroy() {
        stopBreathingAnimation()
        Timber.i("AlarmRingActivity destroyed")
        super.onDestroy()
    }

    companion object {
        private const val BREATHING_UPDATE_INTERVAL_MS = 50L
    }
}

@Composable
private fun AlarmRingScreen(
    alarmId: Long,
    viewModel: AlarmRingViewModel,
    onStop: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Auto-close activity when playback stops
    LaunchedEffect(uiState.isPlaying, uiState.hasPlayback) {
        if (!uiState.isPlaying && !uiState.hasPlayback) {
            Timber.i("Playback stopped, closing AlarmRingActivity")
            delay(500)
            onStop()
        }
    }

    // Start/stop breathing animation based on settings
    LaunchedEffect(uiState.breathingEnabled, uiState.breathingPeriodMs) {
        val activity = context as? AlarmRingActivity
        if (uiState.breathingEnabled) {
            Timber.i("Breathing enabled, period=${uiState.breathingPeriodMs}ms")
            activity?.startBreathingAnimation(uiState.breathingPeriodMs)
        } else {
            Timber.i("Breathing disabled")
            activity?.stopBreathingAnimation()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Current time display
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        Text(
            text = currentTime,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = uiState.alarmLabel.ifEmpty { stringResource(R.string.alarm_default_label) },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Song info
        val songText = uiState.currentSongTitle?.let { title ->
            uiState.currentSongArtist?.let { artist ->
                "$title - $artist"
            } ?: title
        } ?: stringResource(R.string.now_playing)

        Text(
            text = songText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Snooze button
            AlarmControlButton(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Snooze,
                        contentDescription = stringResource(R.string.snooze),
                        modifier = Modifier.size(32.dp)
                    )
                },
                label = stringResource(R.string.snooze_5_min),
                onClick = { viewModel.snooze(context = context, alarmId = alarmId) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )

            // Pause / Resume button
            AlarmControlButton(
                icon = {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) stringResource(R.string.pause) else stringResource(R.string.resume),
                        modifier = Modifier.size(32.dp)
                    )
                },
                label = if (uiState.isPlaying) stringResource(R.string.pause) else stringResource(R.string.resume),
                onClick = {
                    if (uiState.isPlaying) {
                        viewModel.pause(context = context)
                    } else {
                        viewModel.resume(context = context)
                    }
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )

            // Stop button
            AlarmControlButton(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.stop),
                        modifier = Modifier.size(32.dp)
                    )
                },
                label = stringResource(R.string.stop),
                onClick = {
                    viewModel.stop(context = context, alarmId = alarmId)
                    onStop()
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun AlarmControlButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}

package com.rabbithole.musicbbit.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.presentation.alarm.AlarmRingActivity
import com.rabbithole.musicbbit.service.AlarmScheduler
import com.rabbithole.musicbbit.service.PlayMode

@Composable
fun PlayerScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackState by viewModel.stateHolder.playbackState.collectAsStateWithLifecycle()
    val alarmLabel by viewModel.alarmLabel.collectAsStateWithLifecycle()
    val currentSong = playbackState.currentSong

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        // Dismiss button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.player_dismiss)
                )
            }
        }

        // Alarm-active banner (visible only when an alarm is currently playing)
        val context = LocalContext.current
        val alarmId = playbackState.alarmId
        if (alarmId != null) {
            AlarmActiveBanner(
                label = alarmLabel,
                onClick = {
                    val intent = Intent(context, AlarmRingActivity::class.java).apply {
                        putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                onStopAlarm = { viewModel.stateHolder.stop() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Album art placeholder
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Song title and artist
        Text(
            text = currentSong?.title ?: stringResource(R.string.player_unknown_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = currentSong?.artist ?: stringResource(R.string.player_unknown_artist),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Progress slider
        var isUserDragging by remember { mutableStateOf(false) }
        var sliderPosition by remember { mutableFloatStateOf(0f) }
        val positionMs = playbackState.positionMs.toFloat()
        val durationMs = playbackState.durationMs.toFloat().coerceAtLeast(1f)
        if (!isUserDragging) {
            sliderPosition = (positionMs / durationMs).coerceIn(0f, 1f)
        }

        Slider(
            value = sliderPosition,
            onValueChange = {
                isUserDragging = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                viewModel.stateHolder.seekTo((sliderPosition * durationMs).toLong())
                isUserDragging = false
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(playbackState.positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(playbackState.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Playback controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play mode button
            IconButton(onClick = {
                val nextMode = when (playbackState.playMode) {
                    PlayMode.SEQUENTIAL -> PlayMode.RANDOM
                    PlayMode.RANDOM -> PlayMode.REPEAT_ONE
                    PlayMode.REPEAT_ONE -> PlayMode.SEQUENTIAL
                }
                viewModel.stateHolder.setPlayMode(nextMode)
            }) {
                Icon(
                    imageVector = when (playbackState.playMode) {
                        PlayMode.SEQUENTIAL -> Icons.Default.Repeat
                        PlayMode.RANDOM -> Icons.Default.Shuffle
                        PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                    },
                    contentDescription = stringResource(R.string.player_play_mode, playbackState.playMode.name)
                )
            }

            // Previous button
            IconButton(onClick = { viewModel.stateHolder.previous() }) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.player_previous),
                    modifier = Modifier.size(36.dp)
                )
            }

            // Play/Pause button (large)
            IconButton(
                onClick = {
                    if (playbackState.isPlaying) {
                        viewModel.stateHolder.pause()
                    } else {
                        viewModel.stateHolder.resume()
                    }
                },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                    modifier = Modifier.size(48.dp)
                )
            }

            // Next button
            IconButton(onClick = { viewModel.stateHolder.next() }) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.player_next),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
private fun AlarmActiveBanner(
    label: String?,
    onClick: () -> Unit,
    onStopAlarm: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label?.let { "Alarm playing · $it" }
                    ?: stringResource(R.string.alarm_active_banner_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onStopAlarm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(stringResource(R.string.alarm_active_banner_stop))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

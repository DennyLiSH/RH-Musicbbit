package com.rabbithole.musicbbit.presentation.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.ui.res.stringResource
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.navigation.Player

@Composable
fun MiniPlayer(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val alarmLabel by viewModel.alarmLabel.collectAsStateWithLifecycle()
    val currentSong = playbackState.currentSong

    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        val isAlarmMode = playbackState.alarmId != null
        val containerColor = if (isAlarmMode) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
        val albumContainerColor = if (isAlarmMode) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        val albumContentColor = if (isAlarmMode) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .clickable(enabled = currentSong != null) {
                    navController.navigate(Player)
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album art placeholder (alarm mode shows alarm icon with error color)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(albumContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAlarmMode) Icons.Default.Alarm else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = albumContentColor
                )
            }

            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (isAlarmMode) {
                    Text(
                        text = alarmLabel ?: stringResource(R.string.alarm_default_label),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong?.title ?: stringResource(R.string.alarm_active_banner_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = currentSong?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong?.artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Controls
            IconButton(
                onClick = {
                    if (playbackState.isPlaying) {
                        viewModel.pause()
                    } else {
                        viewModel.resume()
                    }
                }
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) stringResource(R.string.mini_player_pause) else stringResource(R.string.mini_player_play)
                )
            }

            IconButton(
                onClick = { viewModel.next() }
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.mini_player_next)
                )
            }

            IconButton(
                onClick = { viewModel.stop() }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.mini_player_close)
                )
            }
        }
    }
}

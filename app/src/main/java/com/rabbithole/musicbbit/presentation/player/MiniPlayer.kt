package com.rabbithole.musicbbit.presentation.player

import com.rabbithole.musicbbit.service.playback.UserPlaybackSession
import com.rabbithole.musicbbit.presentation.playback.LocalPlaybackSession
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.rabbithole.musicbbit.ui.theme.MotionTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
    viewModel: UserPlaybackSession = LocalPlaybackSession.current
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentSong = playbackState.currentSong

    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically(
            animationSpec = tween(MotionTokens.DurationLong, easing = MotionTokens.EasingEmphasized),
            initialOffsetY = { it }
        ),
        exit = slideOutVertically(
            animationSpec = tween(MotionTokens.DurationLong, easing = MotionTokens.EasingEmphasized),
            targetOffsetY = { it }
        )
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(enabled = currentSong != null) {
                    navController.navigate(Player)
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album art placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
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

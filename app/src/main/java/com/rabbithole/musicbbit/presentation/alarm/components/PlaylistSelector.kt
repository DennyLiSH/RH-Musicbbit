package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Playlist

/**
 * A dropdown selector for choosing a playlist.
 * Uses Material3 [ExposedDropdownMenuBox] for a native dropdown experience.
 *
 * States: [isLoading] renders a disabled field (distinguishes loading from a
 * truly empty list), an empty list renders guidance with a create-playlist
 * action, and [isError] marks the field as the source of a validation error.
 *
 * @param playlists List of available playlists
 * @param selectedPlaylistId ID of the currently selected playlist (0 if none)
 * @param isLoading Whether the playlist list has not emitted its first value yet
 * @param isError Whether the field is in the validation-error state
 * @param onPlaylistSelected Callback when a playlist is selected
 * @param onCreatePlaylist Callback for the empty-state create-playlist action
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelector(
    playlists: List<Playlist>,
    selectedPlaylistId: Long,
    isLoading: Boolean,
    isError: Boolean,
    onPlaylistSelected: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedPlaylist = playlists.find { it.id == selectedPlaylistId }
    val displayText = when {
        isLoading -> stringResource(R.string.playlist_selector_loading)
        selectedPlaylist != null -> selectedPlaylist.name
        else -> stringResource(R.string.playlist_selector_placeholder)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (isLoading) {
            OutlinedTextField(
                value = displayText,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.playlist_selector_label)) },
                modifier = Modifier.fillMaxWidth()
            )
        } else if (playlists.isEmpty()) {
            Column {
                Text(
                    text = stringResource(R.string.playlist_selector_create_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                FilledTonalButton(onClick = onCreatePlaylist) {
                    Text(stringResource(R.string.playlist_list_create_button))
                }
            }
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = displayText,
                    onValueChange = {},
                    readOnly = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(stringResource(R.string.alarm_edit_error_select_playlist)) }
                    } else {
                        null
                    },
                    label = { Text(stringResource(R.string.playlist_selector_label)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text(playlist.name) },
                            onClick = {
                                onPlaylistSelected(playlist.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

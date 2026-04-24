package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rabbithole.musicbbit.domain.model.Playlist

/**
 * A dropdown selector for choosing a playlist.
 * Uses Material3 [ExposedDropdownMenuBox] for a native dropdown experience.
 *
 * @param playlists List of available playlists
 * @param selectedPlaylistId ID of the currently selected playlist (0 if none)
 * @param onPlaylistSelected Callback when a playlist is selected
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelector(
    playlists: List<Playlist>,
    selectedPlaylistId: Long,
    onPlaylistSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedPlaylist = playlists.find { it.id == selectedPlaylistId }
    val displayText = selectedPlaylist?.name ?: if (playlists.isEmpty()) {
        "No playlists available"
    } else {
        "Select a playlist"
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (playlists.isEmpty()) {
            Text(
                text = "Please create a playlist first",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = displayText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Playlist") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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

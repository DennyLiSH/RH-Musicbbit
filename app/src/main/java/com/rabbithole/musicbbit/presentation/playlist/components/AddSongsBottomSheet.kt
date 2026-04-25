package com.rabbithole.musicbbit.presentation.playlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.presentation.music.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsBottomSheet(
    availableSongs: List<Song>,
    onSongsSelected: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedSongIds = remember { mutableStateListOf<Long>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.add_songs_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (availableSongs.isEmpty()) {
                Text(
                    text = stringResource(R.string.all_songs_already_in_playlist),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableSongs, key = { it.id }) { song ->
                        val isSelected = song.id in selectedSongIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedSongIds.add(song.id)
                                    } else {
                                        selectedSongIds.remove(song.id)
                                    }
                                }
                            )
                            SongListItem(
                                song = song,
                                onClick = {
                                    if (isSelected) {
                                        selectedSongIds.remove(song.id)
                                    } else {
                                        selectedSongIds.add(song.id)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        TextButton(
                            onClick = { selectedSongIds.addAll(availableSongs.map { it.id }) },
                            enabled = selectedSongIds.size < availableSongs.size
                        ) {
                            Text(stringResource(R.string.select_all))
                        }
                        TextButton(
                            onClick = { selectedSongIds.clear() },
                            enabled = selectedSongIds.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.clear))
                        }
                    }

                    TextButton(
                        onClick = {
                            onSongsSelected(selectedSongIds.toList())
                            onDismiss()
                        },
                        enabled = selectedSongIds.isNotEmpty()
                    ) {
                        Text(
                            text = stringResource(R.string.add_count, selectedSongIds.size)
                        )
                    }
                }
            }
        }
    }
}

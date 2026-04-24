package com.rabbithole.musicbbit.presentation.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.navigation.AlarmEdit
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    navController: NavController,
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("闹钟") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.onAction(AlarmListAction.OnCreateAlarm)
                    navController.navigate(AlarmEdit)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Alarm"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is AlarmListUiState.Loading -> {
                    LoadingContent()
                }

                is AlarmListUiState.Success -> {
                    if (state.alarms.isEmpty()) {
                        EmptyContent()
                    } else {
                        AlarmListContent(
                            alarms = state.alarms,
                            onAlarmClick = { alarmId ->
                                viewModel.onAction(AlarmListAction.OnAlarmClick(alarmId))
                                navController.navigate(AlarmEdit(alarmId = alarmId))
                            },
                            onToggleEnabled = { alarmId, enabled ->
                                viewModel.onAction(AlarmListAction.OnToggleEnabled(alarmId, enabled))
                            },
                            onDeleteAlarm = { alarm ->
                                viewModel.onAction(AlarmListAction.OnDeleteAlarm(alarm))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            modifier = Modifier
                .height(64.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "还没有闹钟",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击 + 按钮创建",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AlarmListContent(
    alarms: List<AlarmItem>,
    onAlarmClick: (Long) -> Unit,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onDeleteAlarm: (Alarm) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = alarms,
            key = { it.alarm.id }
        ) { alarmItem ->
            AlarmListItem(
                alarmItem = alarmItem,
                onClick = { onAlarmClick(alarmItem.alarm.id) },
                onToggleEnabled = { enabled ->
                    onToggleEnabled(alarmItem.alarm.id, enabled)
                },
                onDelete = { onDeleteAlarm(alarmItem.alarm) }
            )
        }
    }
}

@Composable
private fun AlarmListItem(
    alarmItem: AlarmItem,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val alarm = alarmItem.alarm

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: time display
            Text(
                text = formatTime(alarm.hour, alarm.minute),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Center: label + repeat rule + playlist name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.label ?: "闹钟",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatRepeatDays(alarm.repeatDays)} · ${alarmItem.playlistName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Right: enable/disable switch
            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = onToggleEnabled
            )
        }
    }
}

/**
 * Formats the given hour and minute into a 24-hour time string (e.g., "07:30").
 */
private fun formatTime(hour: Int, minute: Int): String {
    return String.format("%02d:%02d", hour, minute)
}

/**
 * Formats a set of [DayOfWeek] into a human-readable repeat description.
 */
private fun formatRepeatDays(days: Set<DayOfWeek>): String {
    return when {
        days.isEmpty() -> "一次性"
        days.size == 7 -> "每天"
        days == setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        ) -> "工作日"
        else -> days.sortedBy { it.value }.joinToString(", ") {
            when (it) {
                DayOfWeek.MONDAY -> "周一"
                DayOfWeek.TUESDAY -> "周二"
                DayOfWeek.WEDNESDAY -> "周三"
                DayOfWeek.THURSDAY -> "周四"
                DayOfWeek.FRIDAY -> "周五"
                DayOfWeek.SATURDAY -> "周六"
                DayOfWeek.SUNDAY -> "周日"
            }
        }
    }
}

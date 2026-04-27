package com.rabbithole.musicbbit.presentation.alarm

import android.os.Build
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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.navigation.AlarmEdit
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    navController: NavController,
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isIgnoringBatteryOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()
    val isFullScreenIntentGranted by viewModel.isFullScreenIntentGranted.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshBatteryOptimizationStatus()
        viewModel.refreshFullScreenIntentStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarm_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.onAction(AlarmListAction.OnCreateAlarm)
                    navController.navigate(AlarmEdit())
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.alarm_list_create_alarm)
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!isIgnoringBatteryOptimizations) {
                            BatteryOptimizationBanner(
                                onClick = {
                                    val intent = viewModel.createBatteryOptimizationIntent()
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !isFullScreenIntentGranted) {
                            FullScreenIntentBanner(
                                onClick = {
                                    FullScreenIntentPermissionHelper.openSettings(context)
                                }
                            )
                        }
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
}

@Composable
private fun BatteryOptimizationBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.battery_optimization_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.battery_optimization_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onClick) {
                Text(stringResource(R.string.go_to_settings))
            }
        }
    }
}

@Composable
private fun FullScreenIntentBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.full_screen_intent_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.full_screen_intent_banner_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onClick) {
                Text(stringResource(R.string.go_to_settings))
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
            text = stringResource(R.string.alarm_empty_title),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.alarm_empty_subtitle),
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
            SwipeableAlarmItem(
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
private fun SwipeableAlarmItem(
    alarmItem: AlarmItem,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val alarm = alarmItem.alarm
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val maxSwipePx = with(LocalDensity.current) { 80.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Background delete layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .matchParentSize()
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        offsetX.animateTo(0f)
                    }
                    onDelete()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete alarm",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Foreground Card
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val threshold = maxSwipePx * 0.3f
                                val target = when {
                                    offsetX.value > threshold -> maxSwipePx
                                    offsetX.value < -threshold -> -maxSwipePx
                                    else -> 0f
                                }
                                offsetX.animateTo(target)
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val newValue = (offsetX.value + dragAmount)
                                .coerceIn(-maxSwipePx, maxSwipePx)
                            offsetX.snapTo(newValue)
                        }
                    }
                },
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
                        text = alarm.label ?: stringResource(R.string.alarm_default_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatRepeatDays(alarm.repeatDays, alarm.excludeHolidays)} · ${alarmItem.playlistName}",
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
@Composable
private fun formatRepeatDays(days: Set<DayOfWeek>, excludeHolidays: Boolean): String {
    return when {
        days.isEmpty() -> stringResource(R.string.alarm_one_time)
        days.size == 7 && !excludeHolidays -> stringResource(R.string.alarm_daily)
        days.size == 7 && excludeHolidays -> stringResource(R.string.alarm_excluding_holidays)
        days == setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        ) -> stringResource(R.string.alarm_weekdays)
        else -> {
            val labels = mutableListOf<String>()
            days.sortedBy { it.value }.forEach { day ->
                val label = when (day) {
                    DayOfWeek.MONDAY -> stringResource(R.string.alarm_monday)
                    DayOfWeek.TUESDAY -> stringResource(R.string.alarm_tuesday)
                    DayOfWeek.WEDNESDAY -> stringResource(R.string.alarm_wednesday)
                    DayOfWeek.THURSDAY -> stringResource(R.string.alarm_thursday)
                    DayOfWeek.FRIDAY -> stringResource(R.string.alarm_friday)
                    DayOfWeek.SATURDAY -> stringResource(R.string.alarm_saturday)
                    DayOfWeek.SUNDAY -> stringResource(R.string.alarm_sunday)
                }
                labels.add(label)
            }
            labels.joinToString(", ")
        }
    }
}

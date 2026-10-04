package com.rabbithole.musicbbit.presentation.alarm

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.navigation.AlarmEdit
import com.rabbithole.musicbbit.presentation.components.CollectUserMessages
import com.rabbithole.musicbbit.presentation.components.EmptyState
import com.rabbithole.musicbbit.presentation.components.InfoBanner
import com.rabbithole.musicbbit.presentation.components.ScreenStateCrossfade
import com.rabbithole.musicbbit.presentation.components.performHapticSafe
import com.rabbithole.musicbbit.presentation.components.rememberAppToast
import com.rabbithole.musicbbit.presentation.permissions.launchSettingsSafely
import com.rabbithole.musicbbit.presentation.util.formatClockTime
import com.rabbithole.musicbbit.service.alarm.QuietModeBypassResolver
import com.rabbithole.musicbbit.ui.theme.MotionTokens
import kotlinx.coroutines.launch
import java.time.DayOfWeek

private val SwipeMaxDistance = 80.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    navController: NavController,
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
    CollectUserMessages(viewModel.messages)
    val context = LocalContext.current
    val toast = rememberAppToast()
    val settingsOpenFailedMessage = stringResource(R.string.common_settings_open_failed)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarm_title)) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(AlarmEdit()) }
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
            ScreenStateCrossfade(
                state = uiState,
                errorIcon = rememberVectorPainter(Icons.Filled.Error),
                onRetry = viewModel::retry,
            ) { alarmItems ->
                Column(modifier = Modifier.fillMaxSize()) {
                    val showBatteryBanner = !permissionStatus.isIgnoringBatteryOptimizations
                    val showFsiBanner = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        !permissionStatus.isFullScreenIntentGranted
                    val showDndBanner = !permissionStatus.isDndAccessGranted &&
                        QuietModeBypassResolver.needsDndAccessBanner(alarmItems.map { it.alarm })
                    if (showBatteryBanner || showFsiBanner || showDndBanner) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (showBatteryBanner) {
                                BatteryOptimizationBanner(
                                    onClick = {
                                        if (!launchSettingsSafely(context, viewModel.createBatteryOptimizationIntent())) {
                                            toast.showShort(settingsOpenFailedMessage)
                                        }
                                    }
                                )
                            }
                            if (showFsiBanner) {
                                if (showBatteryBanner) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                FullScreenIntentBanner(
                                    onClick = {
                                        if (!launchSettingsSafely(context, viewModel.createFullScreenIntentSettingsIntent())) {
                                            toast.showShort(settingsOpenFailedMessage)
                                        }
                                    }
                                )
                            }
                            if (showDndBanner) {
                                if (showBatteryBanner || showFsiBanner) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                DndAccessBanner(
                                    onClick = {
                                        if (!launchSettingsSafely(context, viewModel.createDndAccessSettingsIntent())) {
                                            toast.showShort(settingsOpenFailedMessage)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    if (alarmItems.isEmpty()) {
                        EmptyState(
                            icon = rememberVectorPainter(Icons.Default.Alarm),
                            title = stringResource(R.string.alarm_empty_title),
                            subtitle = stringResource(R.string.alarm_empty_subtitle)
                        )
                    } else {
                        AlarmListContent(
                            alarms = alarmItems,
                            onAlarmClick = { alarmId ->
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
private fun BatteryOptimizationBanner(onClick: () -> Unit) {
    InfoBanner(
        title = R.string.battery_optimization_title,
        message = R.string.battery_optimization_message,
        onClick = onClick,
    )
}

@Composable
private fun FullScreenIntentBanner(onClick: () -> Unit) {
    InfoBanner(
        title = R.string.full_screen_intent_banner_title,
        message = R.string.full_screen_intent_banner_message,
        onClick = onClick,
    )
}

@Composable
private fun DndAccessBanner(onClick: () -> Unit) {
    InfoBanner(
        title = R.string.dnd_access_banner_title,
        message = R.string.dnd_access_banner_message,
        onClick = onClick,
    )
}

@Composable
private fun AlarmListContent(
    alarms: List<AlarmItem>,
    onAlarmClick: (Long) -> Unit,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onDeleteAlarm: (Alarm) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
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
    val maxSwipePx = with(LocalDensity.current) { SwipeMaxDistance.toPx() }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
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
                        offsetX.animateTo(0f, tween(durationMillis = MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasized))
                    }
                    onDelete()
                },
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.alarm_list_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

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
                                val target = if (offsetX.value < -threshold) -maxSwipePx else 0f
                                offsetX.animateTo(target, tween(durationMillis = MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasized))
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val newValue = (offsetX.value + dragAmount)
                                .coerceIn(-maxSwipePx, 0f)
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatClockTime(alarm.hour, alarm.minute),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alarm.label ?: stringResource(R.string.alarm_default_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AlarmListItemSubtitle(alarmItem = alarmItem)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { enabled ->
                        haptic.performHapticSafe(HapticFeedbackType.LongPress)
                        onToggleEnabled(enabled)
                    }
                )
            }
        }
    }
}

@Composable
internal fun AlarmListItemSubtitle(alarmItem: AlarmItem) {
    val base = formatRepeatDays(alarmItem.alarm.repeatDays, alarmItem.alarm.excludeHolidays) +
        " · " + alarmItem.playlistName
    val text = if (!alarmItem.alarm.ignoreQuietMode) {
        base + " · " + stringResource(R.string.alarm_list_muted_in_dnd)
    } else {
        base
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
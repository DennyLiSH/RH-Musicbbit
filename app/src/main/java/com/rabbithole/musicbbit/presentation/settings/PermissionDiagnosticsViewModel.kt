package com.rabbithole.musicbbit.presentation.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.service.AlarmScheduler
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
data class PermissionStatus(
    val name: String,
    val description: String,
    val isGranted: Boolean,
    val isRuntime: Boolean,
    val canRequest: Boolean
)

data class PermissionDiagnosticsUiState(
    val permissions: List<PermissionStatus> = emptyList(),
    val allGranted: Boolean = true
)

@HiltViewModel
class PermissionDiagnosticsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionDiagnosticsUiState())
    val uiState: StateFlow<PermissionDiagnosticsUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        Timber.i("Refreshing permission diagnostics")
        val permissions = buildPermissionList()
        val allGranted = permissions.all { it.isGranted }
        _uiState.update {
            it.copy(permissions = permissions, allGranted = allGranted)
        }
        Timber.d("Permission diagnostics refreshed: allGranted=$allGranted, permissions=${permissions.size}")
    }

    private fun buildPermissionList(): List<PermissionStatus> {
        val list = mutableListOf<PermissionStatus>()

        // 1. Schedule Exact Alarms (API 31+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val canSchedule = alarmScheduler.canScheduleExactAlarms()
            list.add(
                PermissionStatus(
                    name = PERMISSION_NAME_SCHEDULE_EXACT_ALARMS,
                    description = "Required for precise alarm scheduling. Must be enabled in system settings.",
                    isGranted = canSchedule,
                    isRuntime = false,
                    canRequest = false
                )
            )
            Timber.d("Schedule Exact Alarms: granted=$canSchedule")
        }

        // 2. Post Notifications (API 33+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            list.add(
                PermissionStatus(
                    name = PERMISSION_NAME_POST_NOTIFICATIONS,
                    description = "Required to show alarm notifications and playback controls.",
                    isGranted = granted,
                    isRuntime = true,
                    canRequest = true
                )
            )
            Timber.d("Post Notifications: granted=$granted")
        }

        // 3. Read Media Audio / External Storage
        val readMediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val readMediaGranted = ContextCompat.checkSelfPermission(
            context,
            readMediaPermission
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            PermissionStatus(
                name = PERMISSION_NAME_READ_MEDIA_AUDIO,
                description = "Required to access local music files on your device.",
                isGranted = readMediaGranted,
                isRuntime = true,
                canRequest = true
            )
        )
        Timber.d("Read Media Audio: granted=$readMediaGranted")

        // 4. Foreground Service (informational)
        val foregroundServiceGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.FOREGROUND_SERVICE
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            PermissionStatus(
                name = PERMISSION_NAME_FOREGROUND_SERVICE,
                description = "Required for continuous music playback in the background.",
                isGranted = foregroundServiceGranted,
                isRuntime = false,
                canRequest = false
            )
        )
        Timber.d("Foreground Service: granted=$foregroundServiceGranted")

        // 5. Full Screen Intent (API 34+ runtime gate via NotificationManager)
        val fullScreenIntentGranted = FullScreenIntentPermissionHelper.isGranted(context)
        list.add(
            PermissionStatus(
                name = PERMISSION_NAME_FULL_SCREEN_INTENT,
                description = "Required to show the full-screen alarm ringing interface over the lock screen on Android 14+.",
                isGranted = fullScreenIntentGranted,
                isRuntime = false,
                canRequest = true
            )
        )
        Timber.d("Full Screen Intent: granted=$fullScreenIntentGranted")

        // 6. Boot Completed (informational)
        val bootCompletedGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            PermissionStatus(
                name = PERMISSION_NAME_BOOT_COMPLETED,
                description = "Required to restore alarms after device reboot.",
                isGranted = bootCompletedGranted,
                isRuntime = false,
                canRequest = false
            )
        )
        Timber.d("Boot Completed: granted=$bootCompletedGranted")

        return list
    }

    companion object {
        const val PERMISSION_NAME_SCHEDULE_EXACT_ALARMS = "Schedule Exact Alarms"
        const val PERMISSION_NAME_POST_NOTIFICATIONS = "Post Notifications"
        const val PERMISSION_NAME_READ_MEDIA_AUDIO = "Read Media Audio"
        const val PERMISSION_NAME_FOREGROUND_SERVICE = "Foreground Service"
        const val PERMISSION_NAME_FULL_SCREEN_INTENT = "Full Screen Intent"
        const val PERMISSION_NAME_BOOT_COMPLETED = "Boot Completed"
    }
}

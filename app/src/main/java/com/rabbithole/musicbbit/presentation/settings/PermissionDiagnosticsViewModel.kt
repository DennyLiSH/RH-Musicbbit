package com.rabbithole.musicbbit.presentation.settings

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.service.alarm.ports.PermissionPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/** Exhaustive key for every diagnostics row — replaces stringly dispatch. */
enum class PermissionKey {
    SCHEDULE_EXACT_ALARMS,
    POST_NOTIFICATIONS,
    READ_MEDIA_AUDIO,
    FOREGROUND_SERVICE,
    FULL_SCREEN_INTENT,
    BOOT_COMPLETED,
}

@Immutable
data class PermissionDiagnosticItem(
    val key: PermissionKey,
    @StringRes val nameResId: Int,
    @StringRes val descriptionResId: Int,
    val isGranted: Boolean,
    val isRuntime: Boolean,
    val canRequest: Boolean,
)

data class PermissionDiagnosticsUiState(
    val permissions: List<PermissionDiagnosticItem> = emptyList(),
    val allGranted: Boolean = true,
)

@HiltViewModel
class PermissionDiagnosticsViewModel @Inject constructor(
    private val permissionPort: PermissionPort,
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
        _uiState.update { it.copy(permissions = permissions, allGranted = allGranted) }
        Timber.d("Permission diagnostics refreshed: allGranted=$allGranted, permissions=${permissions.size}")
    }

    private fun buildPermissionList(): List<PermissionDiagnosticItem> {
        val list = mutableListOf<PermissionDiagnosticItem>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += item(
                key = PermissionKey.SCHEDULE_EXACT_ALARMS,
                nameResId = R.string.permission_name_exact_alarms,
                descResId = R.string.permission_desc_exact_alarms,
                isGranted = permissionPort.canScheduleExactAlarms(),
                isRuntime = false, canRequest = false,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += item(
                key = PermissionKey.POST_NOTIFICATIONS,
                nameResId = R.string.permission_name_post_notifications,
                descResId = R.string.permission_desc_post_notifications,
                isGranted = permissionPort.checkPermission(Manifest.permission.POST_NOTIFICATIONS),
                isRuntime = true, canRequest = true,
            )
        }
        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        list += item(
            key = PermissionKey.READ_MEDIA_AUDIO,
            nameResId = R.string.permission_name_read_media_audio,
            descResId = R.string.permission_desc_read_media_audio,
            isGranted = permissionPort.checkPermission(mediaPermission),
            isRuntime = true, canRequest = true,
        )
        list += item(
            key = PermissionKey.FOREGROUND_SERVICE,
            nameResId = R.string.permission_name_foreground_service,
            descResId = R.string.permission_desc_foreground_service,
            isGranted = permissionPort.checkPermission(Manifest.permission.FOREGROUND_SERVICE),
            isRuntime = false, canRequest = false,
        )
        list += item(
            key = PermissionKey.FULL_SCREEN_INTENT,
            nameResId = R.string.permission_name_full_screen_intent,
            descResId = R.string.permission_desc_full_screen_intent,
            isGranted = permissionPort.isFullScreenIntentGranted(),
            isRuntime = false, canRequest = true,
        )
        list += item(
            key = PermissionKey.BOOT_COMPLETED,
            nameResId = R.string.permission_name_boot_completed,
            descResId = R.string.permission_desc_boot_completed,
            isGranted = permissionPort.checkPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED),
            isRuntime = false, canRequest = false,
        )
        return list
    }

    private fun item(
        key: PermissionKey,
        @StringRes nameResId: Int,
        @StringRes descResId: Int,
        isGranted: Boolean,
        isRuntime: Boolean,
        canRequest: Boolean,
    ) = PermissionDiagnosticItem(key, nameResId, descResId, isGranted, isRuntime, canRequest)
}
package com.rabbithole.musicbbit.presentation.permissions

import android.Manifest
import android.content.Intent
import android.os.Build
import com.rabbithole.musicbbit.service.alarm.ports.PermissionPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of every permission / system-setting gate the alarm UI shows banners for.
 * One value object replaces the per-ViewModel booleans that used to duplicate the
 * check + refresh boilerplate for each new permission hot spot.
 */
data class PermissionStatus(
    val isIgnoringBatteryOptimizations: Boolean = false,
    val isFullScreenIntentGranted: Boolean = false,
    val isExactAlarmGranted: Boolean = false,
    val isDndAccessGranted: Boolean = false,
    val isMediaAudioGranted: Boolean = false,
)

/**
 * Deep module over [PermissionPort] for the UI layer: one observable [status] flow plus
 * the settings intents behind the same seam. Screens collect [status] once and refresh
 * on ON_RESUME via [refresh]; "open settings" side effects build their [Intent] here
 * instead of reaching static helpers directly.
 *
 * Test adapter: construct with a fake [PermissionPort].
 */
@Singleton
class PermissionStatusMonitor @Inject constructor(
    private val permissionPort: PermissionPort,
) {

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<PermissionStatus> = _status.asStateFlow()

    /** Re-read every flag from the system. Call on ON_RESUME — grants can change while away. */
    fun refresh() {
        _status.value = readStatus()
    }

    /** Intent that opens the system "ignore battery optimizations" dialog for this app. */
    fun createBatteryOptimizationIntent(): Intent = permissionPort.createBatteryOptimizationIntent()

    /** Intent that opens the system page granting Do Not Disturb access. */
    fun createDndAccessSettingsIntent(): Intent = permissionPort.createDndAccessSettingsIntent()

    /** Intent that opens the system page granting SCHEDULE_EXACT_ALARM. */
    fun createExactAlarmSettingsIntent(): Intent = permissionPort.createExactAlarmSettingsIntent()

    /** Intent that opens the system page granting USE_FULL_SCREEN_INTENT (no-op intent on API < 34). */
    fun createFullScreenIntentSettingsIntent(): Intent = permissionPort.createFullScreenIntentSettingsIntent()

    private fun readStatus() = PermissionStatus(
        isIgnoringBatteryOptimizations = permissionPort.isIgnoringBatteryOptimizations(),
        isFullScreenIntentGranted = permissionPort.isFullScreenIntentGranted(),
        isExactAlarmGranted = permissionPort.canScheduleExactAlarms(),
        isDndAccessGranted = permissionPort.isNotificationPolicyAccessGranted(),
        isMediaAudioGranted = permissionPort.checkPermission(mediaAudioPermission()),
    )

    private fun mediaAudioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}

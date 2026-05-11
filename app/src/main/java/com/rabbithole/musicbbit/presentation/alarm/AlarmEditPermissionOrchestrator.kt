package com.rabbithole.musicbbit.presentation.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import com.rabbithole.musicbbit.service.AlarmScheduler
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Encapsulates the permission-check and OEM autostart-guide flow for the alarm edit screen.
 *
 * This is a deep module: a lot of Android-version-specific branching and OEM intent resolution
 * behind a tiny interface (`checkPermissions` / `checkAutostartGuide`).
 */
class AlarmEditPermissionOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmScheduler: AlarmScheduler,
) {

    sealed interface PermissionCheckResult {
        data object AllGranted : PermissionCheckResult
        data object NeedsExactAlarm : PermissionCheckResult
        data object NeedsFullScreenIntent : PermissionCheckResult
    }

    fun checkPermissions(): PermissionCheckResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmScheduler.canScheduleExactAlarms()) {
            return PermissionCheckResult.NeedsExactAlarm
        }
        if (!FullScreenIntentPermissionHelper.isGranted(context)) {
            return PermissionCheckResult.NeedsFullScreenIntent
        }
        return PermissionCheckResult.AllGranted
    }

    sealed interface AutostartGuideResult {
        data class Resolved(val intent: Intent) : AutostartGuideResult
        data object NeedsManualGuide : AutostartGuideResult
        data object NotApplicable : AutostartGuideResult
    }

    fun checkAutostartGuide(): AutostartGuideResult {
        return if (AutostartHelper.isChineseOem()) {
            when (val result = AutostartHelper.getAutostartResult(context)) {
                is AutostartResult.Resolved -> AutostartGuideResult.Resolved(result.intent)
                is AutostartResult.NeedsManualGuide -> AutostartGuideResult.NeedsManualGuide
            }
        } else {
            AutostartGuideResult.NotApplicable
        }
    }
}

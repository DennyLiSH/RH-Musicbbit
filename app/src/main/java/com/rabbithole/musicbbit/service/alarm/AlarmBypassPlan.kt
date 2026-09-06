package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.service.alarm.ports.PermissionPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single answer to "will this alarm actually be audible under silent / DND?"
 * Computed once per fire by [QuietModeBypassResolver]; every consumer — player stream,
 * volume ramp, notification channel — reads from this one value instead of re-deriving
 * the decision from [Alarm.ignoreQuietMode] plus permission checks.
 */
data class AlarmBypassPlan(
    /**
     * Route audio through STREAM_ALARM (bypasses silent mode and most DND filters)
     * rather than STREAM_MUSIC.
     */
    val useAlarmStream: Boolean,
    /**
     * Show the alarm notification on the DND-bypass channel. Requires both the alarm
     * opting in (ignoreQuietMode) and the user granting Notification Policy Access —
     * setBypassDnd is a silent no-op without the grant.
     */
    val useBypassNotificationChannel: Boolean,
)

/**
 * Deep module that turns [Alarm.ignoreQuietMode] + DND access into one [AlarmBypassPlan].
 *
 * Before this module the audibility decision was re-derived independently by
 * [AlarmFireSession] (player stream + volume ramp), [com.rabbithole.musicbbit.service.AlarmNotificationHelper]
 * (notification channel, via a static helper) and the alarm-list banner (inline predicate).
 * The plan is now computed once here and passed down; the banner predicate lives in
 * [needsDndAccessBanner] and is pure JVM-testable code.
 */
@Singleton
class QuietModeBypassResolver @Inject constructor(
    private val permissionPort: PermissionPort,
) {

    fun resolve(alarm: Alarm): AlarmBypassPlan {
        val dndAccessGranted = permissionPort.isNotificationPolicyAccessGranted()
        return AlarmBypassPlan(
            useAlarmStream = alarm.ignoreQuietMode,
            useBypassNotificationChannel = alarm.ignoreQuietMode && dndAccessGranted,
        )
    }

    companion object {

        /**
         * Whether any enabled alarm wants quiet-mode bypass and therefore needs the user
         * to grant DND access for the bypass to actually work.
         */
        fun needsDndAccessBanner(alarms: List<Alarm>): Boolean =
            alarms.any { it.isEnabled && it.ignoreQuietMode }
    }
}

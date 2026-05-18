package com.rabbithole.musicbbit.presentation.alarm

import android.content.Intent
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import timber.log.Timber

/**
 * Orchestrates the alarm save workflow: validation → permission check → persistence → autostart guide.
 *
 * Extracted from [AlarmEditViewModel] to concentrate the save sequence in one place
 * and make it testable without Android UI dependencies.
 */
class AlarmSaveOrchestrator(
    private val alarmRepository: AlarmRepository,
    private val permissionOrchestrator: AlarmEditPermissionOrchestrator,
) {

    sealed interface SaveOutcome {
        /** Playlist not selected. */
        data object MissingPlaylist : SaveOutcome
        /** Exact alarm permission is required. */
        data object NeedsExactAlarmPermission : SaveOutcome
        /** Full-screen intent permission is required. */
        data object NeedsFullScreenIntentPermission : SaveOutcome
        /** Save succeeded; may need autostart guidance. */
        data class Success(val autostart: AutostartOutcome) : SaveOutcome
        /** Save failed. */
        data class Failure(val errorResId: Int) : SaveOutcome
    }

    sealed interface AutostartOutcome {
        data class Resolved(val intent: Intent?) : AutostartOutcome
        data object NeedsManualGuide : AutostartOutcome
        data object NotApplicable : AutostartOutcome
    }

    /**
     * Execute the full save workflow.
     *
     * @param alarm the alarm to save
     * @param playlistId the selected playlist (must be > 0)
     * @return the outcome of the save attempt
     */
    suspend fun save(alarm: Alarm, playlistId: Long): SaveOutcome {
        if (playlistId <= 0) {
            Timber.w("Save failed: no playlist selected")
            return SaveOutcome.MissingPlaylist
        }

        when (val permissionResult = permissionOrchestrator.checkPermissions()) {
            is AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsExactAlarm -> {
                return SaveOutcome.NeedsExactAlarmPermission
            }
            is AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsFullScreenIntent -> {
                return SaveOutcome.NeedsFullScreenIntentPermission
            }
            is AlarmEditPermissionOrchestrator.PermissionCheckResult.AllGranted -> {
                // proceed
            }
        }

        return try {
            alarmRepository.saveAlarm(alarm)
                .fold(
                    onSuccess = { savedId ->
                        Timber.i("Alarm saved successfully, id=%d", savedId)
                        val autostart = mapAutostartResult(permissionOrchestrator.checkAutostartGuide())
                        SaveOutcome.Success(autostart)
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to save alarm")
                        SaveOutcome.Failure(R.string.alarm_edit_error_save_failed)
                    }
                )
        } catch (e: Exception) {
            Timber.e(e, "Failed to save alarm")
            SaveOutcome.Failure(R.string.alarm_edit_error_save_failed)
        }
    }

    private fun mapAutostartResult(
        result: AlarmEditPermissionOrchestrator.AutostartGuideResult
    ): AutostartOutcome = when (result) {
        is AlarmEditPermissionOrchestrator.AutostartGuideResult.Resolved ->
            AutostartOutcome.Resolved(result.intent)
        is AlarmEditPermissionOrchestrator.AutostartGuideResult.NeedsManualGuide ->
            AutostartOutcome.NeedsManualGuide
        is AlarmEditPermissionOrchestrator.AutostartGuideResult.NotApplicable ->
            AutostartOutcome.NotApplicable
    }
}

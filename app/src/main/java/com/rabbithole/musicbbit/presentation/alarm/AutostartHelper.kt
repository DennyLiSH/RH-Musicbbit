package com.rabbithole.musicbbit.presentation.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import timber.log.Timber

sealed class AutostartResult {
    data class Resolved(val intent: Intent) : AutostartResult()
    data object NeedsManualGuide : AutostartResult()
}

object AutostartHelper {

    private val CHINESE_OEMS = setOf(
        "xiaomi", "redmi", "poco",
        "huawei", "honor",
        "oppo", "realme", "oneplus",
        "vivo", "iqoo"
    )

    private val XIAOMI_INTENTS = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
    )

    private val HUAWEI_INTENTS = listOf(
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
    )

    private val ONEPLUS_INTENTS = listOf(
        "com.oplus.battery" to "com.oplus.startupapp.view.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
    )

    private val OPPO_INTENTS = listOf(
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
    )

    private val VIVO_INTENTS = listOf(
        "com.vivo.abe" to "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.vivo.abe" to "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManager",
    )

    fun isChineseOem(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val result = CHINESE_OEMS.any { manufacturer.contains(it) }
        Timber.d("AutostartHelper: manufacturer=%s, isChineseOem=%b", manufacturer, result)
        return result
    }

    fun getAutostartResult(context: Context): AutostartResult {
        val candidates = getIntentCandidatesForOem()
        if (candidates.isEmpty()) {
            Timber.i("AutostartHelper: no candidates for this OEM, needs manual guide")
            return AutostartResult.NeedsManualGuide
        }

        for ((index, pair) in candidates.withIndex()) {
            val (pkg, activity) = pair
            val intent = oemIntent(pkg, activity)
            if (isResolvable(context, intent)) {
                Timber.i(
                    "AutostartHelper: resolved intent #%d for %s: %s/%s",
                    index + 1, android.os.Build.MANUFACTURER, pkg, activity
                )
                return AutostartResult.Resolved(intent)
            }
            Timber.d(
                "AutostartHelper: intent #%d not resolvable: %s/%s",
                index + 1, pkg, activity
            )
        }

        Timber.i("AutostartHelper: all intents failed for %s, needs manual guide", android.os.Build.MANUFACTURER)
        return AutostartResult.NeedsManualGuide
    }

    fun getManualGuideSettingsIntent(): Intent {
        return Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun getIntentCandidatesForOem(): List<Pair<String, String>> {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
                XIAOMI_INTENTS
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                HUAWEI_INTENTS
            manufacturer.contains("oneplus") ->
                ONEPLUS_INTENTS
            manufacturer.contains("oppo") || manufacturer.contains("realme") ->
                OPPO_INTENTS
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                VIVO_INTENTS
            else -> emptyList()
        }
    }

    private fun oemIntent(packageName: String, activityName: String): Intent {
        return Intent().apply {
            component = ComponentName(packageName, activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun isResolvable(context: Context, intent: Intent): Boolean {
        return context.packageManager.resolveActivity(intent, 0) != null
    }
}

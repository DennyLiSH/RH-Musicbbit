package com.rabbithole.musicbbit.presentation.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import timber.log.Timber

object AutostartHelper {

    private val CHINESE_OEMS = setOf(
        "xiaomi", "redmi", "poco",
        "huawei", "honor",
        "oppo", "realme", "oneplus",
        "vivo", "iqoo"
    )

    fun isChineseOem(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val result = CHINESE_OEMS.any { manufacturer.contains(it) }
        Timber.d("AutostartHelper: manufacturer=%s, isChineseOem=%b", manufacturer, result)
        return result
    }

    fun getAutostartIntent(context: Context): Intent {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val oemIntent = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
                oemIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                oemIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") ->
                oemIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                oemIntent("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")
            else -> null
        }

        if (oemIntent != null && isResolvable(context, oemIntent)) {
            Timber.i("AutostartHelper: using OEM-specific intent for %s", manufacturer)
            return oemIntent
        }

        Timber.i("AutostartHelper: falling back to app settings for %s", manufacturer)
        return fallbackAppSettings(context)
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

    private fun fallbackAppSettings(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

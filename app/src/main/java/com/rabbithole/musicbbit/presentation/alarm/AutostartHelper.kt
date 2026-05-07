package com.rabbithole.musicbbit.presentation.alarm

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

    fun getAutostartIntent(context: Context): Intent? {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                // Try MIUI autostart intent first
                try {
                    Intent("miui.intent.action.OP_AUTO_START").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "AutostartHelper: MIUI autostart intent not available")
                    fallbackAppSettings(context)
                }
            }
            else -> fallbackAppSettings(context)
        }
    }

    private fun fallbackAppSettings(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

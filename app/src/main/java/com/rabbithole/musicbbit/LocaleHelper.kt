package com.rabbithole.musicbbit

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.rabbithole.musicbbit.presentation.settings.AppLanguage
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun applyOnStartup(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val tag = readTag(context)
        if (tag.isEmpty()) return
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
    }

    fun wrapContext(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val tag = readTag(context)
        if (tag.isEmpty()) return context
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    fun getCurrentLanguage(context: Context): AppLanguage {
        val tag = readTag(context)
        return AppLanguage.entries.find { it.tag == tag } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(activity: Activity, language: AppLanguage) {
        writeTag(activity, language.tag)
        val localeList = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.create()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun readTag(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""
    }

    private fun writeTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, tag)
            .apply()
    }
}

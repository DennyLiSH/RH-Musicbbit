package com.rabbithole.musicbbit.presentation.components

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Lightweight wrapper around android.widget.Toast for Compose UI.
 *
 * Contract: message MUST be localized static text from strings.xml.
 * DO NOT pass user input, exception stacktraces, or PII — Toast text can
 * be read by other apps via accessibility services.
 */
class AppToast(private val context: Context) {
    fun showShort(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun showLong(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

/**
 * Returns an [AppToast] bound to the current Composition's [Context].
 *
 * Caller constraint: must be called from a Composition backed by an
 * Activity (Preview / UI Test without Activity context will get the
 * framework's no-op LocalContext — Toast calls silently fail there).
 * Design invariant (guaranteed by project structure): all Screens are
 * loaded via [com.rabbithole.musicbbit.navigation.AppNavigation] from
 * MainActivity, so Activity-backed Context is always present.
 */
@Composable
fun rememberAppToast(): AppToast {
    val context = LocalContext.current
    return remember(context) { AppToast(context) }
}

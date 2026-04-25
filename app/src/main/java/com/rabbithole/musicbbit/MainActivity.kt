package com.rabbithole.musicbbit

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rabbithole.musicbbit.domain.model.ThemeMode
import com.rabbithole.musicbbit.navigation.AppNavigation
import com.rabbithole.musicbbit.presentation.settings.ThemeViewModel
import com.rabbithole.musicbbit.ui.theme.音乐兔Theme
import com.rabbithole.musicbbit.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkFullScreenIntentPermission()
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            音乐兔Theme(darkTheme = darkTheme) {
                AppNavigation()
            }
        }
    }

    /**
     * Check whether the app can use full-screen intents on API 34+.
     * If not, show a toast to guide the user to enable it in notification settings.
     */
    private fun checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                Timber.w("Full screen intent permission not granted on API 34+")
                Toast.makeText(
                    this,
                    getString(R.string.fullscreen_notification_permission_toast),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

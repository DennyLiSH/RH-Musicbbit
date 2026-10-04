package com.rabbithole.musicbbit

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.rabbithole.musicbbit.navigation.AppNavigation
import com.rabbithole.musicbbit.presentation.settings.ThemeViewModel
import com.rabbithole.musicbbit.presentation.settings.appDarkTheme
import com.rabbithole.musicbbit.ui.theme.音乐兔Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    @javax.inject.Inject
    lateinit var playbackSession: com.rabbithole.musicbbit.service.playback.UserPlaybackSession

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            音乐兔Theme(darkTheme = themeViewModel.appDarkTheme()) {
                AppNavigation(playbackSession = playbackSession)
            }
        }
    }
}

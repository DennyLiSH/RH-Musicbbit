package com.rabbithole.musicbbit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rabbithole.musicbbit.navigation.AppNavigation
import com.rabbithole.musicbbit.ui.theme.音乐兔Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            音乐兔Theme {
                AppNavigation()
            }
        }
    }
}

package com.youtubeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.youtubeapp.ui.navigation.AppNavigation
import com.youtubeapp.ui.theme.YouTubeAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YouTubeAppTheme {
                AppNavigation()
            }
        }
    }
}

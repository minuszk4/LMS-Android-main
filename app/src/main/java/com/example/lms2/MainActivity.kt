package com.example.lms2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.lms2.ui.theme.LMSTheme
import com.example.lms2.ui.navigation.AppNavGraph
import com.example.lms2.util.AppSettingsStore
import com.example.lms2.util.CloudinaryManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CloudinaryManager.init(this)

        enableEdgeToEdge()
        setContent {
            var darkModeEnabled by remember { mutableStateOf(AppSettingsStore.isDarkModeEnabled(this)) }

            LMSTheme(darkTheme = darkModeEnabled) {
                AppNavGraph(
                    onDarkModeChanged = { enabled ->
                        darkModeEnabled = enabled
                    }
                )
            }
        }
    }
}

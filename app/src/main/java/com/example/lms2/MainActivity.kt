package com.example.lms2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.lms2.ui.theme.LMSTheme
import com.example.lms2.ui.navigation.AppNavGraph
import com.example.lms2.util.CloudinaryManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CloudinaryManager.init(this)

        enableEdgeToEdge()
        setContent {
            LMSTheme {
                AppNavGraph()
            }
        }
    }
}

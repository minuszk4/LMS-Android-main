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

/**
 * Activity gốc khởi tạo lớp giao diện Compose của ứng dụng LMS Android.
 * File này thiết lập các thành phần dùng chung ở mức toàn ứng dụng như Cloudinary,
 * edge-to-edge và điểm vào của `AppNavGraph` cùng hệ thống theme.
 * Đây là đầu mối quan trọng nối lifecycle Android truyền thống với kiến trúc Compose của dự án.
 */

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

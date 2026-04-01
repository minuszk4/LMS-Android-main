package com.example.lms2.ui.screen.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.lms2.ui.component.TopBar
import com.example.lms2.util.AppSettingsStore

private val SettingsBg = Color(0xFFF8FAFC)
private val SettingsCardBg = Color(0xFFEFF3FF)
private val SettingsPrimary = Color(0xFF4B5CC4)
private val SettingsTextPrimary = Color(0xFF1E293B)
private val SettingsTextSecondary = Color(0xFF64748B)
private val SettingsRowBg = Color.White
private val SettingsRowBorder = Color(0xFFE2E8F0)

@Composable
fun StudentSettingsScreen(
    onDarkModeChanged: (Boolean) -> Unit = {},
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colorScheme = MaterialTheme.colorScheme

    var pushNotificationEnabled by remember {
        mutableStateOf(AppSettingsStore.isPushNotificationEnabled(context))
    }
    var emailNotificationEnabled by remember {
        mutableStateOf(AppSettingsStore.isEmailNotificationEnabled(context))
    }
    var darkModeEnabled by remember {
        mutableStateOf(AppSettingsStore.isDarkModeEnabled(context))
    }

    Scaffold(
        topBar = {
            TopBar(title = "Cài đặt", onBackClick = onBackClick)
        },
        containerColor = colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionHeader(title = "Thông báo")
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.Notifications,
                    title = "Bật thông báo",
                    checked = pushNotificationEnabled,
                    onCheckedChange = {
                        pushNotificationEnabled = it
                        AppSettingsStore.setPushNotificationEnabled(context, it)
                    }
                )
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.Mail,
                    title = "Thông báo email",
                    checked = emailNotificationEnabled,
                    onCheckedChange = {
                        emailNotificationEnabled = it
                        AppSettingsStore.setEmailNotificationEnabled(context, it)
                    }
                )
            }

            item {
                SectionHeader(title = "Tùy chọn")
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.DarkMode,
                    title = "Chế độ tối",
                    checked = darkModeEnabled,
                    onCheckedChange = {
                        darkModeEnabled = it
                        AppSettingsStore.setDarkModeEnabled(context, it)
                        onDarkModeChanged(it)
                    }
                )
            }
            item {
                SettingActionRow(
                    icon = Icons.Default.Language,
                    title = "Ngôn ngữ",
                    trailingText = "Tiếng Việt",
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    onClick = {
                        Toast.makeText(context, "Hiện chỉ hỗ trợ Tiếng Việt", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                SectionHeader(title = "Hỗ trợ")
            }
            item {
                SettingActionRow(
                    icon = Icons.Default.PrivacyTip,
                    title = "Chính sách bảo mật",
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = {
                        runCatching {
                            uriHandler.openUri("https://www.termsfeed.com/live/8d8e5e5a-1e35-4c4e-8f94-4d52f4d0b999")
                        }.onFailure {
                            Toast.makeText(context, "Không thể mở liên kết", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            item {
                SettingActionRow(
                    icon = Icons.Default.Description,
                    title = "Điều khoản dịch vụ",
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = {
                        runCatching {
                            uriHandler.openUri("https://www.termsfeed.com/live/c8f817f1-9b4a-4c79-9a72-7a2cf9680c31")
                        }.onFailure {
                            Toast.makeText(context, "Không thể mở liên kết", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            item {
                SettingActionRow(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = "Trung tâm trợ giúp",
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    onClick = {
                        runCatching {
                            uriHandler.openUri("https://support.google.com")
                        }.onFailure {
                            Toast.makeText(context, "Không thể mở liên kết", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp)
    )
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colorScheme.surface)
            .border(0.5.dp, colorScheme.outline.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingLeadingIcon(icon = icon)
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colorScheme.primary,
                uncheckedThumbColor = colorScheme.surface,
                uncheckedTrackColor = colorScheme.outline,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SettingActionRow(
    icon: ImageVector,
    title: String,
    trailingText: String = "",
    trailingIcon: ImageVector,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colorScheme.surface)
            .border(0.5.dp, colorScheme.outline.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingLeadingIcon(icon = icon)
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface
        )

        if (trailingText.isNotBlank()) {
            Text(
                text = trailingText,
                color = colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingLeadingIcon(icon: ImageVector) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}



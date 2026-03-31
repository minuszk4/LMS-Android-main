package com.example.lms.ui.screen.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lms.ui.component.TopBar

private val SettingsBg = Color(0xFFF8FAFC)
private val SettingsCardBg = Color(0xFFEFF3FF)
private val SettingsPrimary = Color(0xFF4B5CC4)
private val SettingsTextPrimary = Color(0xFF1E293B)
private val SettingsTextSecondary = Color(0xFF64748B)
private val SettingsRowBg = Color.White
private val SettingsRowBorder = Color(0xFFE2E8F0)

@Composable
fun StudentSettingsScreen(
    onBackClick: () -> Unit
) {
    var pushNotificationEnabled by remember { mutableStateOf(true) }
    var emailNotificationEnabled by remember { mutableStateOf(false) }
    var darkModeEnabled by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBar(title = "Cài đặt", onBackClick = onBackClick)
        },
        containerColor = SettingsBg
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
                    onCheckedChange = { pushNotificationEnabled = it }
                )
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.Mail,
                    title = "Thông báo email",
                    checked = emailNotificationEnabled,
                    onCheckedChange = { emailNotificationEnabled = it }
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
                    onCheckedChange = { darkModeEnabled = it }
                )
            }
            item {
                SettingActionRow(
                    icon = Icons.Default.Language,
                    title = "Ngôn ngữ",
                    trailingText = "Tiếng Việt",
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForwardIos
                )
            }

            item {
                SectionHeader(title = "Hỗ trợ")
            }
            item {
                SettingActionRow(
                    icon = Icons.Default.PrivacyTip,
                    title = "Chính sách bảo mật",
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                )
            }
            item {
                SettingActionRow(
                    icon = Icons.Default.Description,
                    title = "Điều khoản dịch vụ",
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                )
            }
            item {
                SettingActionRow(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = "Trung tâm trợ giúp",
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForwardIos
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = SettingsTextPrimary,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SettingsRowBg)
            .border(1.dp, SettingsRowBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingLeadingIcon(icon = icon)
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = SettingsTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SettingsPrimary,
                uncheckedThumbColor = Color(0xFFF8FAFC),
                uncheckedTrackColor = Color(0xFFD1D5DB),
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
    trailingIcon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SettingsRowBg)
            .border(1.dp, SettingsRowBorder, RoundedCornerShape(14.dp))
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
            color = Color(0xFF111827)
        )

        if (trailingText.isNotBlank()) {
            Text(
                text = trailingText,
                color = SettingsTextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            tint = SettingsTextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingLeadingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE4E7FF)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF4B5CC4),
            modifier = Modifier.size(18.dp)
        )
    }
}



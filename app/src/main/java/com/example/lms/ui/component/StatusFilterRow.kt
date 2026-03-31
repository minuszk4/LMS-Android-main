package com.example.lms.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class CourseFilterStatus(val displayName: String) {
    ALL("Tất cả"),
    PUBLISHED("Đang hiển thị"),
    DRAFT("Bản nháp")
}

private val Primary = Color(0xFF4B5CC4)

@Composable
fun StatusFilterRow(
    selectedStatus: CourseFilterStatus,
    onStatusSelected: (CourseFilterStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(CourseFilterStatus.entries) { status ->
            FilterChip(
                selected = selectedStatus == status,
                onClick = { onStatusSelected(status) },
                label = {
                    Text(
                        text = status.displayName,
                        fontSize = 14.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Primary,
                    selectedLabelColor = Color.White,
                    containerColor = Color.White,
                    labelColor = Color.Gray
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedStatus == status,
                    borderColor = if (selectedStatus == status) Color.Transparent else Color.LightGray,
                    selectedBorderColor = Color.Transparent
                )
            )
        }
    }
}

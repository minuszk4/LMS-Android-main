package com.example.lms2.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cung cấp composable tái sử dụng ProgressBar cho giao diện LMS Android.
 * Thành phần trong file này được dùng chung ở nhiều màn hình để giữ trải nghiệm hiển thị đồng nhất.
 * Tách riêng component giúp giảm lặp code và dễ bảo trì hơn khi thay đổi thiết kế.
 */

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    backgroundColor: Color = Color(0xFF4B5CC4).copy(alpha = 0.15f),
    progressColor: Color = Color(0xFF4B5CC4),
    cornerRadius: Dp = 50.dp,
    animate: Boolean = true
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "progress_animation"
    )

    val displayProgress = if (animate) animatedProgress else progress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(displayProgress)
                .fillMaxHeight()
                .background(progressColor)
        )
    }
}

package com.example.lms2.util

import com.example.lms2.data.model.CourseAnalyticsData

/**
 * Mô tả trạng thái giao diện của luồng CourseAnalytics.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

data class CourseAnalyticsUiState(
    val isLoading: Boolean = false,
    val analytics: CourseAnalyticsData? = null
)


package com.example.lms2.util

import com.example.lms2.data.model.InstructorAnalyticsData

/**
 * Mô tả trạng thái giao diện của luồng InstructorAnalytics.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

enum class InstructorTimeRange(val label: String, val days: Int?) {
    LAST_7_DAYS("7 ngày", 7),
    LAST_30_DAYS("30 ngày", 30),
    LAST_90_DAYS("90 ngày", 90),
    ALL_TIME("Tất cả", null)
}

/**
 * Khai báo InstructorAnalyticsUiState trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class InstructorAnalyticsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedRange: InstructorTimeRange = InstructorTimeRange.LAST_30_DAYS,
    val analytics: InstructorAnalyticsData = InstructorAnalyticsData(),
    val hasLoadedOnce: Boolean = false
) {
    val isEmpty: Boolean
        get() = analytics.kpi.totalCourses == 0
}


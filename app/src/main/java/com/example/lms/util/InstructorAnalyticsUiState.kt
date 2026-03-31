package com.example.lms.util

import com.example.lms.data.model.InstructorAnalyticsData

enum class InstructorTimeRange(val label: String, val days: Int?) {
    LAST_7_DAYS("7 ngày", 7),
    LAST_30_DAYS("30 ngày", 30),
    LAST_90_DAYS("90 ngày", 90),
    ALL_TIME("Tất cả", null)
}

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


package com.example.lms.util

import com.example.lms.data.model.CourseAnalyticsData

data class CourseAnalyticsUiState(
    val isLoading: Boolean = false,
    val analytics: CourseAnalyticsData? = null
)


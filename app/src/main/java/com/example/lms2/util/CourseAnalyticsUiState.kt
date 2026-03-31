package com.example.lms2.util

import com.example.lms2.data.model.CourseAnalyticsData

data class CourseAnalyticsUiState(
    val isLoading: Boolean = false,
    val analytics: CourseAnalyticsData? = null
)


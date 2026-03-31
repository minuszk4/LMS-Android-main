package com.example.lms.util

sealed class CourseAnalyticsEvent {
    data class ShowError(val message: String) : CourseAnalyticsEvent()
}


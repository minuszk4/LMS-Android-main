package com.example.lms2.util

sealed class CourseAnalyticsEvent {
    data class ShowError(val message: String) : CourseAnalyticsEvent()
}


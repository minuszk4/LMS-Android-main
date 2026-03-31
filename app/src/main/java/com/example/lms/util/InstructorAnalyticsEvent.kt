package com.example.lms.util

sealed class InstructorAnalyticsEvent {
    data class ShowError(val message: String) : InstructorAnalyticsEvent()
}


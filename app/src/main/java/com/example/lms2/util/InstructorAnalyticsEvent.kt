package com.example.lms2.util

sealed class InstructorAnalyticsEvent {
    data class ShowError(val message: String) : InstructorAnalyticsEvent()
}


package com.example.lms.util

sealed class CourseEvent {
    object SaveSuccess : CourseEvent()
    data class ShowError(val message: String) : CourseEvent()
}

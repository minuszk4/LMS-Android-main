package com.example.lms2.util

sealed class CourseEvent {
    object SaveSuccess : CourseEvent()
    data class ShowError(val message: String) : CourseEvent()
}

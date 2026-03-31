package com.example.lms.util

sealed class CourseDetailEvent {
    object EnrollSuccess : CourseDetailEvent()
    data class NavigateToPayment(val selectedCourseIds: List<String>) : CourseDetailEvent()
    data class ShowMessage(val message: String) : CourseDetailEvent()
    data class ShowError(val message: String) : CourseDetailEvent()
}
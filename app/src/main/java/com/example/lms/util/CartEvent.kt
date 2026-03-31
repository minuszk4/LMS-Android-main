package com.example.lms.util

sealed class CartEvent {
    data class ShowError(val message: String) : CartEvent()
    data class ItemRemoved(val courseId: String, val courseTitle: String) : CartEvent()
    data class NavigateToPayment(val selectedCourseIds: List<String>) : CartEvent()
}


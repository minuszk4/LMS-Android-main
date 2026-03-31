package com.example.lms2.util

sealed class LessonEvent {
    data class ShowSnackbar(val message: String) : LessonEvent()
    object SaveSuccess : LessonEvent()
}

package com.example.lms.util

sealed class LessonEvent {
    data class ShowSnackbar(val message: String) : LessonEvent()
    object SaveSuccess : LessonEvent()
}
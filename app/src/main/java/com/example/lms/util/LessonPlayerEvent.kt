package com.example.lms.util

sealed class LessonPlayerEvent {
    data class ShowError(val message: String) : LessonPlayerEvent()
    object ToggleLessonCompleteSuccess : LessonPlayerEvent()
    object SaveQuizResultSuccess : LessonPlayerEvent()
}
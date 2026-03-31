package com.example.lms.util

sealed class QuizEvent {
    data class ShowSnackbar(val message: String) : QuizEvent()
    object SaveSuccess : QuizEvent()
}

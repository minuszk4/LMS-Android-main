package com.example.lms2.util

sealed class QuizEvent {
    data class ShowSnackbar(val message: String) : QuizEvent()
    object SaveSuccess : QuizEvent()
}

package com.example.lms2.util

sealed class QuizAttemptEvent {
    data class ShowError(val message: String) : QuizAttemptEvent()
    object SubmitQuizSuccess : QuizAttemptEvent()
    object QuizTimeUp : QuizAttemptEvent()
    object RetakeQuizSuccess : QuizAttemptEvent()
}

package com.example.lms.util

import com.example.lms.data.model.Question
import com.example.lms.data.model.QuizProgress

data class QuizAttemptUiState(
    val quizId: String = "",
    val courseId: String = "",
    val userId: String = "",
    val quizTitle: String = "",
    val questions: List<Question> = emptyList(),
    val durationSeconds: Int = 0,
    val passingScore: Int = 0,

    val isLoadingQuiz: Boolean = false,
    val loadErrorMessage: String? = null,

    val quizProgress: QuizProgress? = null,
    
    // Attempt State
    val currentQuestionIndex: Int = 0,
    val selectedAnswers: Map<Int, Int> = emptyMap(),
    val remainingSeconds: Int = 0,
    val isTimerRunning: Boolean = false,

    val isSubmitting: Boolean = false,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val score: Int = 0,

    val showResults: Boolean = false,
    val isRetaking: Boolean = false
)

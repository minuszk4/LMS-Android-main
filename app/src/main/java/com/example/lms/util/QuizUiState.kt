package com.example.lms.util

import com.example.lms.data.model.Question

data class QuizUiState(
    val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val description: String = "",
    val questions: List<Question> = emptyList(),
    val durationMinutes: String = "15",
    val passingScore: String = "80",
    val orderIndex: Int = 0,
    
    // UI Status
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    
    // Validation Errors
    val titleError: String? = null,
    val durationError: String? = null,
    val passingScoreError: String? = null,
    val questionsError: String? = null
)

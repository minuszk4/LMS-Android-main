package com.example.lms.util

import com.example.lms.data.model.Instructor

data class InstructorPersonalInfoUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val instructor: Instructor? = null
)


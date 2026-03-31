package com.example.lms2.util

import com.example.lms2.data.model.Instructor

data class InstructorPersonalInfoUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val instructor: Instructor? = null
)


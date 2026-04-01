package com.example.lms2.util

import com.example.lms2.data.model.Course
import com.example.lms2.data.model.Instructor

data class InstructorPublicProfileUiState(
    val isLoading: Boolean = false,
    val instructor: Instructor? = null,
    val courses: List<Course> = emptyList()
)


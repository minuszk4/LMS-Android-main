package com.example.lms.util

sealed class CurriculumEvent {
    data class ShowSnackbar(val message: String) : CurriculumEvent()
}
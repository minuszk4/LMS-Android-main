package com.example.lms2.util

sealed class CurriculumEvent {
    data class ShowSnackbar(val message: String) : CurriculumEvent()
}
